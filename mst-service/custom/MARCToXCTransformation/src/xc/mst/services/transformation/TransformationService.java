/**
 * Copyright (c) 2009 eXtensible Catalog Organization
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the
 * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
 * website http://www.extensiblecatalog.org/.
 *
 */

package xc.mst.services.transformation;

import gnu.trove.TLongHashSet;
import gnu.trove.TLongLongHashMap;
import gnu.trove.TLongProcedure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import xc.mst.bo.provider.Format;
import xc.mst.bo.record.AggregateXCRecord;
import xc.mst.bo.record.InputRecord;
import xc.mst.bo.record.OutputRecord;
import xc.mst.bo.record.Record;
import xc.mst.bo.record.RecordCounts;
import xc.mst.bo.record.RecordMessage;
import xc.mst.bo.record.SaxMarcXmlRecord;
import xc.mst.bo.service.Service;
import xc.mst.dao.DataException;
import xc.mst.dao.DatabaseConfigException;
import xc.mst.manager.IndexException;
import xc.mst.services.impl.service.SolrTransformationService;
import xc.mst.services.transformation.dao.TransformationDAO;
import xc.mst.utils.MSTConfiguration;
import xc.mst.utils.TimingLogger;
import xc.mst.utils.XmlHelper;

/**
 * A Metadata Service which for each unprocessed marcxml record creates an XC schema
 * record from the data in the unprocessed record.
 *
 * @author Eric Osisek
 * @author Benjamin D. Anderson
 */
public class TransformationService extends SolrTransformationService {

    private final static Logger LOG = Logger.getLogger(TransformationService.class);

    protected XmlHelper xmlHelper = new XmlHelper();

    // TODO - these datastructures need to be read in and they need to be persisted.
    // which begs the question about the lack of transactions... I need a way to
    // rollback if something bad happens. Probably the easiest thing to do is just to delete
    // records with some id higher than something.

    // 001 to man record_id; reflects db state
    protected Map<String, TLongLongHashMap> bibsProcessedLongIdMap = new HashMap<String, TLongLongHashMap>();
    protected Map<String, Map<String, Long>> bibsProcessedStringIdMap = new HashMap<String, Map<String, Long>>();

    // 001 to man record_id; reflects db state
    protected Map<String, TLongLongHashMap> bibsYet2ArriveLongIdMap = new HashMap<String, TLongLongHashMap>();
    protected Map<String, Map<String, Long>> bibsYet2ArriveStringIdMap = new HashMap<String, Map<String, Long>>();

    // for adding to the above maps
    protected Map<String, TLongLongHashMap> bibsProcessedLongIdAddedMap = new HashMap<String, TLongLongHashMap>();
    protected Map<String, Map<String, Long>> bibsProcessedStringIdAddedMap = new HashMap<String, Map<String, Long>>();
    protected Map<String, TLongLongHashMap> bibsYet2ArriveLongIdAddedMap = new HashMap<String, TLongLongHashMap>();
    protected Map<String, Map<String, Long>> bibsYet2ArriveStringIdAddedMap = new HashMap<String, Map<String, Long>>();

    // for removing to the above maps
    protected Map<String, TLongLongHashMap> bibsProcessedLongIdRemovedMap = new HashMap<String, TLongLongHashMap>();
    protected Map<String, Map<String, Long>> bibsProcessedStringIdRemovedMap = new HashMap<String, Map<String, Long>>();
    protected Map<String, TLongLongHashMap> bibsYet2ArriveLongIdRemovedMap = new HashMap<String, TLongLongHashMap>();
    protected Map<String, Map<String, Long>> bibsYet2ArriveStringIdRemovedMap = new HashMap<String, Map<String, Long>>();

    // XC's org code
    public static final String XC_SOURCE_OF_MARC_ORG = "NyRoXCO";

    protected TLongLongHashMap getLongKeyedMap(String key, Map<String, TLongLongHashMap> m1) {
        TLongLongHashMap m2 = m1.get(key);
        if (m2 == null) {
            m2 = new TLongLongHashMap();
            m1.put(key, m2);
        }
        return m2;
    }

    protected Map<String, Long> getStringKeyedMap(String key, Map<String, Map<String, Long>> m1) {
        Map<String, Long> m2 = m1.get(key);
        if (m2 == null) {
            m2 = new HashMap<String, Long>();
            m1.put(key, m2);
        }
        return m2;
    }

    protected TLongHashSet previouslyHeldManifestationIds = new TLongHashSet();
    protected List<long[]> heldHoldings = new ArrayList<long[]>();
    protected Format xcFormat = null;

    protected TransformationDAO transformationDAO = null;

    protected int inputBibs = 0;
    protected int inputHoldings = 0;

    protected int outputWorks = 0;
    protected int outputExpressions = 0;
    protected int outputManifestations = 0;

    public void setTransformationDAO(TransformationDAO transformationDAO) {
        this.transformationDAO = transformationDAO;
    }

    public TransformationDAO getTransformationDAO() {
        return this.transformationDAO;
    }

    @Override
    public void init() {
        super.init();
        // Initialize the XC format
        try {
            xcFormat = getFormatService().getFormatByName("xc");
        } catch (DatabaseConfigException e) {
            LOG.error("Could not connect to the database with the parameters in the configuration file.", e);
        }
    }

    @Override
    public void setup() {
        LOG.info("TransformationService.setup");
        TimingLogger.outputMemory();
        TimingLogger.start("getTransformationDAO().loadBibMaps");
        getTransformationDAO().loadBibMaps(
                bibsProcessedLongIdMap,
                bibsProcessedStringIdMap,
                bibsYet2ArriveLongIdMap,
                bibsYet2ArriveStringIdMap);
        TimingLogger.stop("getTransformationDAO().loadBibMaps");
        TimingLogger.reset();
        inputBibs = getRepository().getPersistentPropertyAsInt("inputBibs", 0);
        inputHoldings = getRepository().getPersistentPropertyAsInt("inputHoldings", 0);
    }

    protected Long getLongFromMap(TLongLongHashMap longLongMap, Map<String, Long> stringLongMap, String s) {
        try {
            Long bibMarcId = Long.parseLong(s.trim());
            long l = longLongMap.get(bibMarcId);
            if (l == 0) {
                return null;
            } else {
                return (Long) l;
            }
        } catch (NumberFormatException nfe) {
            return stringLongMap.get(s);
        } catch (NullPointerException npe) {
            return null;
        }
    }

    private void add2Map(TLongLongHashMap longLongMap, Map<String, Long> stringLongMap,
            TLongLongHashMap longLongMapAdded, Map<String, Long> stringLongMapAdded, String s, long lv) {
        try {
            Long bibMarcId = Long.parseLong(s.trim());
            longLongMap.put(bibMarcId, lv);
            longLongMapAdded.put(bibMarcId, lv);
        } catch (NumberFormatException nfe) {
            stringLongMap.put(s, lv);
            stringLongMapAdded.put(s, lv);
        }
    }

    private void removeFromMap(TLongLongHashMap longLongMap, Map<String, Long> stringLongMap,
            TLongLongHashMap longLongMapRemoved, Map<String, Long> stringLongMapRemoved, String s) {
        try {
            Long bibMarcId = Long.parseLong(s.trim());
            longLongMap.remove(bibMarcId);
            longLongMapRemoved.remove(bibMarcId);
        } catch (NumberFormatException nfe) {
            stringLongMap.remove(s);
            stringLongMapRemoved.remove(s);
        }
    }

    protected Long getManifestationId4BibProcessed(String orgCode, String s) {
        return getLongFromMap(
                getLongKeyedMap(orgCode, bibsProcessedLongIdMap),
                getStringKeyedMap(orgCode, bibsProcessedStringIdMap),
                s);
    }

    protected void addManifestationId4BibProcessed(String orgCode, String s, Long l) {
        add2Map(
                getLongKeyedMap(orgCode, bibsProcessedLongIdMap),
                getStringKeyedMap(orgCode, bibsProcessedStringIdMap),
                getLongKeyedMap(orgCode, bibsProcessedLongIdAddedMap),
                getStringKeyedMap(orgCode, bibsProcessedStringIdAddedMap),
                s, l);
    }

    protected void removeManifestationId4BibProcessed(String orgCode, String s) {
        removeFromMap(
                getLongKeyedMap(orgCode, bibsProcessedLongIdMap),
                getStringKeyedMap(orgCode, bibsProcessedStringIdMap),
                getLongKeyedMap(orgCode, bibsProcessedLongIdRemovedMap),
                getStringKeyedMap(orgCode, bibsProcessedStringIdRemovedMap),
                s);
    }

    protected Long getManifestationId4BibYet2Arrive(String orgCode, String s) {
        return getLongFromMap(
                getLongKeyedMap(orgCode, bibsYet2ArriveLongIdMap),
                getStringKeyedMap(orgCode, bibsYet2ArriveStringIdMap),
                s);
    }

    protected void addManifestationId4BibYet2Arrive(String orgCode, String s, Long l) {
        add2Map(
                getLongKeyedMap(orgCode, bibsYet2ArriveLongIdMap),
                getStringKeyedMap(orgCode, bibsYet2ArriveStringIdMap),
                getLongKeyedMap(orgCode, bibsYet2ArriveLongIdAddedMap),
                getStringKeyedMap(orgCode, bibsYet2ArriveStringIdAddedMap),
                s, l);
    }

    protected void removeManifestationId4BibYet2Arrive(String orgCode, String s, Long l) {
        removeFromMap(
                getLongKeyedMap(orgCode, bibsYet2ArriveLongIdMap),
                getStringKeyedMap(orgCode, bibsYet2ArriveStringIdMap),
//                getLongKeyedMap(orgCode, bibsYet2ArriveLongIdRemovedMap),   // this makes no sense?
//                getStringKeyedMap(orgCode, bibsYet2ArriveStringIdRemovedMap),
                getLongKeyedMap(orgCode, bibsYet2ArriveLongIdAddedMap),
                getStringKeyedMap(orgCode, bibsYet2ArriveStringIdAddedMap),
                s);
        //TODO  test this fix: do you need to add these to removed map?  I would think so in case they got persisted.
        add2Map(
                getLongKeyedMap(orgCode, bibsYet2ArriveLongIdMap),
                getStringKeyedMap(orgCode, bibsYet2ArriveStringIdMap),
                getLongKeyedMap(orgCode, bibsYet2ArriveLongIdRemovedMap),
                getStringKeyedMap(orgCode, bibsYet2ArriveStringIdRemovedMap),
                s, l);
// LOG.info("** END removeManifestationId4BibYet2Arrive "+s);
    }

    @Override
    protected boolean commitIfNecessary(boolean force, long processedRecordsCount) {
        if (!force) {
            return super.commitIfNecessary(force, 0);
        }
        try {
            TimingLogger.start("TransformationDAO.endBatch");

            TimingLogger.start("TransformationDAO.non-generic");
            // persist 4 001->recordId maps
            getTransformationDAO().persistBibMaps(
                    bibsProcessedLongIdAddedMap, bibsProcessedStringIdAddedMap,
                    bibsProcessedLongIdRemovedMap, bibsProcessedStringIdRemovedMap,
                    bibsYet2ArriveLongIdAddedMap, bibsYet2ArriveStringIdAddedMap,
                    bibsYet2ArriveLongIdRemovedMap, bibsYet2ArriveStringIdRemovedMap);

            bibsProcessedLongIdAddedMap.clear();
            bibsProcessedStringIdAddedMap.clear();
            bibsProcessedLongIdRemovedMap.clear();
            bibsProcessedStringIdRemovedMap.clear();
            bibsYet2ArriveLongIdAddedMap.clear();
            bibsYet2ArriveStringIdAddedMap.clear();
            bibsYet2ArriveLongIdRemovedMap.clear();
            bibsYet2ArriveStringIdRemovedMap.clear();

            previouslyHeldManifestationIds.forEach(new TLongProcedure() {
                public boolean execute(long recordId) {
                    LOG.debug("previouslyHeldManifestationId: " + recordId + "");
                    return true;
                }
            });
            getTransformationDAO().persistHeldHoldings(heldHoldings);
            getTransformationDAO().getHoldingIdsToActivate(previouslyHeldManifestationIds).forEach(
                    new TLongProcedure() {
                        public boolean execute(long recordId) {
                            LOG.debug("getRepository().activateRecord(" + recordId + ")");
                            getRepository().activateRecord("holdings", recordId);
                            return true;
                        }
                    });
            TimingLogger.start("TransformationDAO.non-generic");
            super.commitIfNecessary(true, 0);
            TimingLogger.stop("TransformationDAO.non-generic");
            heldHoldings.clear();
            getTransformationDAO().deleteHeldHoldings(previouslyHeldManifestationIds);
            previouslyHeldManifestationIds.clear();
            /*
            // TODO: use polymorphism instead
            if (!(getRepository() instanceof TestRepository)) {
                super.endBatch();
            }
            getTransformationDAO().persistHeldHoldings(heldHoldings);
            heldHoldings.clear();
            getTransformationDAO().deleteHeldHoldings(previouslyHeldManifestationIds);
            if (getRepository() instanceof TestRepository) {
                super.endBatch();
            }
            */

            TimingLogger.stop("TransformationDAO.non-generic");
            TimingLogger.stop("TransformationDAO.endBatch");

            getRepository().setPersistentProperty("inputBibs", inputBibs);
            getRepository().setPersistentProperty("inputHoldings", inputHoldings);
            TimingLogger.reset();
        } catch (Throwable t) {
            getUtil().throwIt(t);
        }
        return true;
    }

    @Override
    public List<OutputRecord> process(InputRecord record) {
        // addErrorToInput(record, 12, RecordMessage.WARN);
        // addErrorToInput(record, 13, RecordMessage.ERROR, "the input is fubed");
        LOG.debug("getHarvestedOaiIdentifier(): " + ((Record) record).getHarvestedOaiIdentifier());
        LOG.debug("getOaiIdentifier(): " + ((Record) record).getOaiIdentifier());
        LOG.debug("getId(): " + ((Record) record).getId());
        try {
            List<OutputRecord> results = new ArrayList<OutputRecord>();

            if (Record.DELETED == record.getStatus()) {
                if (record.getSuccessors() != null) {
                    for (OutputRecord or : record.getSuccessors()) {
                        or.setStatus(Record.DELETED);
                        results.add(or);
                        Record r = getRepository().getRecord(or.getId());
                        String type = getXCRecordService().getType(r);
                        or.setType(type);
                    }
                }
            } else {
                record.setMode(Record.STRING_MODE);

                String sourceOfRecords = null;
                if (config.getPropertyAsInt("SourceOf9XXFields", 0) == 1)
                	sourceOfRecords = XC_SOURCE_OF_MARC_ORG;
                
                SaxMarcXmlRecord originalRecord = new SaxMarcXmlRecord(record.getOaiXml(), sourceOfRecords);

                // Get the ORG code from the 035 field
                orgCode = originalRecord.getOrgCode();
                if (StringUtils.isEmpty(orgCode)) {
                    // Add error
                    // record.addError(service.getId() + "-100: An organization code could not be found on either the 003 or 035 field of input MARC record.");
                }

                boolean isBib = false;
                boolean isHolding = false;

                char leader06 = originalRecord.getLeader().charAt(6);
                if ("abcdefghijkmnoprt".contains("" + leader06)) {
                    isBib = true;
                } else if (leader06 == 'u' || leader06 == 'v' || leader06 == 'x' || leader06 == 'y') {
                    isHolding = true;
                } else { // If leader 6th character is invalid, then log error and do not process that record.
                    logDebug("Record Id " + record.getId() + " with leader character " + leader06 + " not processed.");
                    return results;
                }

                AggregateXCRecord ar = new AggregateXCRecord();
                if (isBib) {
                    ((Record) record).setType("bib");
                    processBibliographicRecord(ar, originalRecord, record);
                } else if (isHolding) {
                    ((Record) record).setType("hold");
                    processHoldingRecord(ar, originalRecord, record);
                }

                if (record.getSuccessors() != null && record.getSuccessors().size() > 0) {
                    for (OutputRecord or : record.getSuccessors()) {
                        Record succ = getRepository().getRecord(or.getId());
                        // ignore deleted successors
                        // since they were deleted, so we need to ignore them forever
                        // (new successors and OAI IDs get generated whenever a deleted record later gets re-activated)
                        if (! succ.getDeleted()) {
	                        String type = getXCRecordService().getType(succ);
	                        or.setType(type);
	                        if (AggregateXCRecord.HOLDINGS.equals(type)) {
	                            ar.getPreviousHoldingIds().add(or.getId());
	                        } else if (AggregateXCRecord.MANIFESTATION.equals(type)) {
	                            ar.setPreviousManifestationId(or.getId());
	                        } else if (AggregateXCRecord.EXPRESSION.equals(type)) {
	                            ar.getPreviousExpressionIds().add(or.getId());
	                        } else if (AggregateXCRecord.WORK.equals(type)) {
	                            ar.getPreviousWorkIds().add(or.getId());
	                        } else {
	                            throw new RuntimeException("bogus");
	                        }
                        }
                    }
                } else {
                    inputRecordCount++;
                    if (isBib) {
                        inputBibs++;
                    } else if (isHolding) {
                        inputHoldings++;
                    }
                }
                long nextNewId = getRepositoryDAO().getNextId();
                if (isBib) {
                	// if this record is missing a 001, we need to use its 035$a instead (issue mst-473)
                	// because holdings records may need to reference 035$a in addition to 001
                    List<String> bib001s = originalRecord.getBib001_or_035s();
                    String bib001 = "";
                    
                    final String orgCode = originalRecord.getOrgCode();
                    Long manifestationId = null;
                    
                    if (!StringUtils.isEmpty(orgCode)) {
                    	// try to find a manifestationId based on either the 001 or 035s (issue mst-473)...
                    	for (String thisBib001 : bib001s) {
                        	bib001 = thisBib001;
                    	
	                        manifestationId = getManifestationId4BibYet2Arrive(
	                                originalRecord.getOrgCode(), bib001);
	                        
	                        if (manifestationId != null) {
	                        	break;
	                        }
                    	}
	                        
                        //TODO test more!
//          LOG.info("bib arrived, 001="+bib001+" orgcode="+originalRecord.getOrgCode()+" manifestId found in bibsyet2arrive: "+manifestationId);
                        if (manifestationId != null) {
                            TimingLogger.add("found BibYet2Arrive", 1);
                            removeManifestationId4BibYet2Arrive(
                                    originalRecord.getOrgCode(), bib001, manifestationId);
                            previouslyHeldManifestationIds.add(manifestationId);
//             LOG.info("think we added bibYet2Arrive to previouslyHeldManifestationIds ! "+manifestationId);
                        } else {
                            if (ar.getPreviousManifestationId() != null) {
                                manifestationId = ar.getPreviousManifestationId();
                            } else {
                                manifestationId = getRepositoryDAO().getNextIdAndIncr();
                            }
                        }
                        
                        // store the 001 or all 035s for this bib (issue mst-473)
                        for (String thisBib001 : bib001s) {
	                        addManifestationId4BibProcessed(
	                                originalRecord.getOrgCode(), thisBib001, manifestationId);
                        }
                        
                    }
                    List<OutputRecord> bibRecords = getXCRecordService().getSplitXCRecordXML(
                            getRepository(), ar, manifestationId, nextNewId);    // note, we are now adding poss. of a null manifestationId
                    if (bibRecords != null) {
                        results.addAll(bibRecords);
                    }
                } else if (isHolding) {
                    char status = Record.ACTIVE;
                    List<Long> manifestationIds = new ArrayList<Long>();
                    List<Long> manifestationsIdsInWaiting = new ArrayList<Long>();
                    if (ar.getReferencedBibs() == null) {
                        LOG.error("ar.getReferencedBibs() == null");
                    } else {
                        for (String ref001 : ar.getReferencedBibs()) {
                            final String orgCode = originalRecord.getOrgCode();
                            if (!StringUtils.isEmpty(orgCode)) {
                                Long manifestationId = getManifestationId4BibProcessed(
                                        orgCode, ref001);

                                LOG.debug("input " + record.getId() + "manifestationId: " + manifestationId);
                                if (manifestationId == null) {
                                    manifestationId = getManifestationId4BibYet2Arrive(
                                            orgCode, ref001);
                                    status = Record.HELD;
                                    if (manifestationId == null) {
                                        manifestationId = getRepositoryDAO().getNextIdAndIncr();
                                        addManifestationId4BibYet2Arrive(
                                                orgCode, ref001, manifestationId);
                                    }
                                    manifestationsIdsInWaiting.add(manifestationId);
                                }
                                manifestationIds.add(manifestationId);
                            }
                        }
                        List<OutputRecord> holdingsRecords = getXCRecordService().getSplitXCRecordXMLForHoldingRecord(
                                getRepository(), ar, manifestationIds, nextNewId);

                        if (holdingsRecords != null) {
                            for (OutputRecord r : holdingsRecords) {
                                // addErrorToOutput(r, 16, RecordMessage.INFO);
                                // addErrorToOutput(r, 17, RecordMessage.INFO, "the output is fubed");
                                if (status == Record.HELD) {
                                    for (Long mid : manifestationsIdsInWaiting) {
                                        heldHoldings.add(new long[] { r.getId(), mid });
                                    }
                                }
                                r.setStatus(status);
                                results.add(r);
                            }
                        } else {
                            LOG.debug("holdingsRecords == null");
                        }
                    }
                }
                // update service accordingly w/ new record counts
            }
            TimingLogger.add("output records", results.size());
            for (OutputRecord or : results) {
                if (!or.getDeleted()) {
                    String type = getXCRecordService().getType((Record) or);
                    or.setType(type);
                }
                or.setFormat(xcFormat);
            }
            if (results.size() == 0) {
                addMessage(record, 102, RecordMessage.ERROR);
            }
            return results;
        } catch (Throwable t) {
            LOG.error("error processing record with id:" + ((Record) record).getId(), t);
            addMessage(record, 102, RecordMessage.ERROR);
        }
        return null;
    }
    
    /*
     * Process bibliographic record
     */
    protected void processBibliographicRecord(
            AggregateXCRecord transformedRecord, SaxMarcXmlRecord originalRecord, InputRecord record)
                throws DataException, DatabaseConfigException, TransformerConfigurationException,
                    IndexException, TransformerException {

        // Run the transformation steps
        // Each one processes a different MARC XML field and adds the appropriate
        // XC fields to transformedRecord based on the field it processes.
        transformedRecord = process010(originalRecord, transformedRecord);
        transformedRecord = process015(originalRecord, transformedRecord);
        transformedRecord = process016(originalRecord, transformedRecord);
        transformedRecord = process022(originalRecord, transformedRecord);
        transformedRecord = process024(originalRecord, transformedRecord);
        transformedRecord = process028(originalRecord, transformedRecord);
        transformedRecord = process030(originalRecord, transformedRecord);
        transformedRecord = process035(originalRecord, transformedRecord);
        transformedRecord = process037(originalRecord, transformedRecord);
        transformedRecord = process050(originalRecord, transformedRecord);
        transformedRecord = process055(originalRecord, transformedRecord);
        transformedRecord = process060(originalRecord, transformedRecord);
        transformedRecord = process074(originalRecord, transformedRecord);
        transformedRecord = process082(originalRecord, transformedRecord);
        transformedRecord = process084(originalRecord, transformedRecord);
        transformedRecord = process086(originalRecord, transformedRecord);
        transformedRecord = process090(originalRecord, transformedRecord);
        transformedRecord = process092(originalRecord, transformedRecord);
        transformedRecord = process100(originalRecord, transformedRecord);
        transformedRecord = process110(originalRecord, transformedRecord);
        transformedRecord = process111(originalRecord, transformedRecord);
        transformedRecord = process130(originalRecord, transformedRecord);
        transformedRecord = process210(originalRecord, transformedRecord);
        transformedRecord = process222(originalRecord, transformedRecord);
        transformedRecord = process240(originalRecord, transformedRecord);
        transformedRecord = process243(originalRecord, transformedRecord);
        transformedRecord = process245(originalRecord, transformedRecord);
        transformedRecord = process246(originalRecord, transformedRecord);
        transformedRecord = process247(originalRecord, transformedRecord);
        transformedRecord = process250(originalRecord, transformedRecord);
        transformedRecord = process254(originalRecord, transformedRecord);
        transformedRecord = process255(originalRecord, transformedRecord);
        transformedRecord = process260(originalRecord, transformedRecord);
        transformedRecord = process300(originalRecord, transformedRecord);
        transformedRecord = process310(originalRecord, transformedRecord);
        transformedRecord = process321(originalRecord, transformedRecord);
        transformedRecord = process362(originalRecord, transformedRecord);
        transformedRecord = process440(originalRecord, transformedRecord);
        transformedRecord = process490(originalRecord, transformedRecord);
        transformedRecord = process500(originalRecord, transformedRecord);
        transformedRecord = process501(originalRecord, transformedRecord);
        transformedRecord = process502(originalRecord, transformedRecord);
        transformedRecord = process504(originalRecord, transformedRecord);
        transformedRecord = process505(originalRecord, transformedRecord);
        transformedRecord = process506(originalRecord, transformedRecord);
        transformedRecord = process507(originalRecord, transformedRecord);
        transformedRecord = process508(originalRecord, transformedRecord);
        transformedRecord = process510(originalRecord, transformedRecord);
        transformedRecord = process511(originalRecord, transformedRecord);
        transformedRecord = process513(originalRecord, transformedRecord);
        transformedRecord = process515(originalRecord, transformedRecord);
        transformedRecord = process518(originalRecord, transformedRecord);
        transformedRecord = process520(originalRecord, transformedRecord);
        transformedRecord = process521(originalRecord, transformedRecord);
        transformedRecord = process522(originalRecord, transformedRecord);
        transformedRecord = process525(originalRecord, transformedRecord);
        transformedRecord = process530(originalRecord, transformedRecord);
        transformedRecord = process533(originalRecord, transformedRecord);
        transformedRecord = process534(originalRecord, transformedRecord);
        transformedRecord = process538(originalRecord, transformedRecord);
        transformedRecord = process540(originalRecord, transformedRecord);
        transformedRecord = process544(originalRecord, transformedRecord);
        transformedRecord = process546(originalRecord, transformedRecord);
        transformedRecord = process547(originalRecord, transformedRecord);
        transformedRecord = process550(originalRecord, transformedRecord);
        transformedRecord = process555(originalRecord, transformedRecord);
        transformedRecord = process580(originalRecord, transformedRecord);
        transformedRecord = process586(originalRecord, transformedRecord);
        transformedRecord = process59X(originalRecord, transformedRecord);
        transformedRecord = process600(originalRecord, transformedRecord);
        transformedRecord = process610(originalRecord, transformedRecord);
        transformedRecord = process611(originalRecord, transformedRecord);
        transformedRecord = process630(originalRecord, transformedRecord);
        transformedRecord = process648(originalRecord, transformedRecord);
        transformedRecord = process650(originalRecord, transformedRecord);
        transformedRecord = process651(originalRecord, transformedRecord);
        transformedRecord = process653(originalRecord, transformedRecord);
        transformedRecord = process654(originalRecord, transformedRecord);
        transformedRecord = process655(originalRecord, transformedRecord);
        transformedRecord = process720(originalRecord, transformedRecord);
        transformedRecord = process740(originalRecord, transformedRecord);
        transformedRecord = process752(originalRecord, transformedRecord);
        transformedRecord = process760(originalRecord, transformedRecord);
        transformedRecord = process765(originalRecord, transformedRecord, record);
        transformedRecord = process770(originalRecord, transformedRecord, record);
        transformedRecord = process772(originalRecord, transformedRecord, record);
        transformedRecord = process773(originalRecord, transformedRecord, record);
        transformedRecord = process775(originalRecord, transformedRecord, record);
        transformedRecord = process776(originalRecord, transformedRecord, record);
        transformedRecord = process777(originalRecord, transformedRecord, record);
        transformedRecord = process780(originalRecord, transformedRecord, record);
        transformedRecord = process785(originalRecord, transformedRecord);
        transformedRecord = process786(originalRecord, transformedRecord, record);
        transformedRecord = process787(originalRecord, transformedRecord, record);
        transformedRecord = process800(originalRecord, transformedRecord);
        transformedRecord = process810(originalRecord, transformedRecord);
        transformedRecord = process811(originalRecord, transformedRecord);
        transformedRecord = process830(originalRecord, transformedRecord);
        transformedRecord = process852(originalRecord, transformedRecord, record);
        transformedRecord = process856(originalRecord, transformedRecord);
        transformedRecord = process866(originalRecord, transformedRecord);
        transformedRecord = process867(originalRecord, transformedRecord);
        transformedRecord = process868(originalRecord, transformedRecord);
        transformedRecord = process931(originalRecord, transformedRecord);
        transformedRecord = process932(originalRecord, transformedRecord);
        transformedRecord = process933(originalRecord, transformedRecord);
        transformedRecord = process934(originalRecord, transformedRecord);
        transformedRecord = process935(originalRecord, transformedRecord);
        transformedRecord = process937(originalRecord, transformedRecord);
        transformedRecord = process939(originalRecord, transformedRecord);
        transformedRecord = process943(originalRecord, transformedRecord);
        transformedRecord = process945(originalRecord, transformedRecord);
        transformedRecord = process947(originalRecord, transformedRecord);
        transformedRecord = process959(originalRecord, transformedRecord);
        transformedRecord = process963(originalRecord, transformedRecord);
        transformedRecord = process965(originalRecord, transformedRecord);
        transformedRecord = process967(originalRecord, transformedRecord);
        transformedRecord = process969(originalRecord, transformedRecord);
        transformedRecord = process977(originalRecord, transformedRecord);
        transformedRecord = process700(originalRecord, transformedRecord);
        transformedRecord = process710(originalRecord, transformedRecord);
        transformedRecord = process711(originalRecord, transformedRecord);
        transformedRecord = process730(originalRecord, transformedRecord);

        if (config.getPropertyAsInt("SourceOfBibRecordIDs", 0) == 1)
    		transformedRecord = process001And003(originalRecord, transformedRecord);

        // this should be the final step in line!
    	if (config.getPropertyAsInt("DedupRecordIDs", 0) == 1)
    		transformedRecord = dedupRecordIDs(originalRecord, transformedRecord);

    }

    /*
     * Process holding record
     */
    protected void processHoldingRecord(
            AggregateXCRecord transformedRecord,
            SaxMarcXmlRecord originalRecord,
            InputRecord record)
                throws DatabaseConfigException, TransformerConfigurationException,
                    IndexException, TransformerException, DataException {

        // Run the transformation steps
        // Each one processes a different MARC XML field and adds the appropriate
        // XC fields to transformedRecord based on the field it processes.
        holdingsProcess004(originalRecord, transformedRecord);
        holdingsProcess014(originalRecord, transformedRecord);
        transformedRecord = holdingsProcess506(originalRecord, transformedRecord);
        transformedRecord = holdingsProcess852(originalRecord, transformedRecord, record);
        transformedRecord = holdingsProcess856(originalRecord, transformedRecord);
        transformedRecord = process866(originalRecord, transformedRecord);
        transformedRecord = process867(originalRecord, transformedRecord);
        transformedRecord = process868(originalRecord, transformedRecord);
        transformedRecord = holdingsProcess001And003(originalRecord, transformedRecord);
        /* holdingsProcess843 is commented for now. This will be implemented later.
        transformedRecord = holdingsProcess843(originalRecord, transformedRecord);
        */
    }

    protected void applyRulesToRecordCounts(RecordCounts mostRecentIncomingRecordCounts) {

        if (MSTConfiguration.getInstance().getPropertyAsBoolean("rule_checking_enabled", false)) {
            final Logger LOG2 = getRulesLogger();

            try {
                RecordCounts rcIn, rcOut = null;

                try {
                    Service s = service;
                    if (s == null) {
                        LOG2.error("*** can not calculate record counts, no service found");
                        return;
                    }
                    rcIn = getRecordCountsDAO().getTotalIncomingRecordCounts(s.getName());
                    if (rcIn == null) {
                        LOG2.error("*** can not calculate record counts null recordCounts returned for service: " + s.getName());
                        return;
                    }
                    rcOut = getRecordCountsDAO().getTotalOutgoingRecordCounts(s.getName());
                    if (rcOut == null) {
                        LOG2.error("*** can not calculate record counts null recordCounts returned for service: " + s.getName());
                        return;
                    }
                } catch (Exception e) {
                    LOG2.error("*** can not calculate record counts: ", e);
                    return;
                }
                // TODO: bug fix? all UNEXPECTED_ERROR retrieved counts are null!
                Map<String, AtomicInteger> counts4typeIn_tot = rcIn.getCounts().get(RecordCounts.TOTALS);
                Map<String, AtomicInteger> counts4typeIn_b = rcIn.getCounts().get("bib");
                Map<String, AtomicInteger> counts4typeIn_h = rcIn.getCounts().get("hold");
                Map<String, AtomicInteger> counts4typeOut_t = rcOut.getCounts().get(RecordCounts.TOTALS);
                Map<String, AtomicInteger> counts4typeOut_e = rcOut.getCounts().get("expression");
                Map<String, AtomicInteger> counts4typeOut_w = rcOut.getCounts().get("work");
                Map<String, AtomicInteger> counts4typeOut_m = rcOut.getCounts().get("manifestation");
                Map<String, AtomicInteger> counts4typeOut_h = rcOut.getCounts().get("holdings");

                // TODO this belongs in dynamic script so it can be modified easily - pass array of values to script.
                LOG2.info("%%%");
                LOG2.info(MSTConfiguration.getInstance().getProperty("message.ruleCheckingHeaderTransformation"));
                LOG2.info(MSTConfiguration.getInstance().getProperty("message.ruleTransformationTBIA_eq_TMA"));// = Trans Bibs In Active = Transformation Manifestations Active
                String result = "";
                try {
                    if (counts4typeIn_b.get(RecordCounts.NEW_ACTIVE).get() == counts4typeOut_m.get(RecordCounts.NEW_ACTIVE).get()) {
                        result = " ** PASS **";
                    } else {
                        result = " ** FAIL **";
                    }
                    LOG2.info("TBIA=" + counts4typeIn_b.get(RecordCounts.NEW_ACTIVE) + ", TMA=" + counts4typeOut_m.get(RecordCounts.NEW_ACTIVE) + result);
                } catch (Exception e) {
                    LOG2.info("Could not calculate previous rule, null data");
                }

                LOG2.info(MSTConfiguration.getInstance().getProperty("message.ruleTransformationTBID_eq_TMD"));// = Trans Bibs In Deleted = Transformation Manifestations Deleted
                try {
                    if (counts4typeIn_b.get(RecordCounts.NEW_DELETE).get() == counts4typeOut_m.get(RecordCounts.NEW_DELETE).get()) {
                        result = " ** PASS **";
                    } else {
                        result = " ** FAIL **";
                    }
                    LOG2.info("TBID=" + counts4typeIn_b.get(RecordCounts.NEW_DELETE) + ", TMD=" + counts4typeOut_m.get(RecordCounts.NEW_DELETE) + result);
                } catch (Exception e) {
                    LOG2.info("Could not calculate previous rule, null data");
                }

                LOG2.info(MSTConfiguration.getInstance().getProperty("message.ruleTransformationTHIA_leq_THOA_THH"));
                //=Trans HoldingsActive<=Trans Holdings Out Active + Transformation Holdings Held
                try {
                    final int n_h_a = counts4typeIn_h.get(RecordCounts.NEW_ACTIVE).get();
                    final int t_h_a = counts4typeOut_h.get(RecordCounts.NEW_ACTIVE).get();
                    final int t_h_h = counts4typeOut_h.get(RecordCounts.NEW_HELD).get();
                    if (n_h_a <= (t_h_a + t_h_h)) {
                        result = " ** PASS **";
                    } else {
                        result = " ** FAIL **";
                    }
                    LOG2.info("THIA=" + n_h_a + ", THOA=" + t_h_a + ", THH=" + t_h_h + result);
                } catch (Exception e) {
                    LOG2.info("Could not calculate previous rule, null data");
                }

                LOG2.info(MSTConfiguration.getInstance().getProperty("message.ruleTransformationTEA_eq_TWA"));// = Transformation Expressions Active = Transformation Works Active
                try {
                    if (counts4typeOut_e.get(RecordCounts.NEW_ACTIVE).get() == counts4typeOut_w.get(RecordCounts.NEW_ACTIVE).get()) {
                        result = " ** PASS **";
                    } else {
                        result = " ** FAIL **";
                    }
                    LOG2.info("TEA=" + counts4typeOut_e.get(RecordCounts.NEW_ACTIVE) + ", TWA=" + counts4typeOut_w.get(RecordCounts.NEW_ACTIVE) + result);
                } catch (Exception e) {
                    LOG2.info("Could not calculate previous rule, null data");
                }

                LOG2.info(MSTConfiguration.getInstance().getProperty("message.ruleTransformationTWA_geq_TMA"));// = Transformation Works Active >= Transformation Manifestations Active
                int t_m_a;
                int t_e_a;
                try {
                    t_m_a = counts4typeOut_m.get(RecordCounts.NEW_ACTIVE).get();
                    final int t_w_a = counts4typeOut_w.get(RecordCounts.NEW_ACTIVE).get();
                    t_e_a = counts4typeOut_e.get(RecordCounts.NEW_ACTIVE).get();
                    if (t_w_a >= (t_m_a)) {
                        result = " ** PASS **";
                    } else {
                        result = " ** FAIL **";
                    }
                    LOG2.info("TWA=" + t_w_a + ", TMA=" + t_m_a + result);

                    LOG2.info(MSTConfiguration.getInstance().getProperty("message.ruleTransformationTEA_geq_TMA"));// = Transformation Expressions Active >= Transformation Manifestations Active
                    if (t_e_a >= (t_m_a)) {
                        result = " ** PASS **";
                    } else {
                        result = " ** FAIL **";
                    }
                    LOG2.info("TWA=" + t_e_a + ", TMA=" + t_m_a + result);
                } catch (Exception e) {
                    LOG2.info("Could not calculate previous rule, null data");
                }

                LOG2.info("%%%");

            } catch (Exception e) {
                LOG.error("", e);
                LOG2.error("", e);
            }
        }
    }
}
