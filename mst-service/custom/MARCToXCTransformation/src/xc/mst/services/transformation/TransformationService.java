/**
 * Copyright (c) 2009 eXtensible Catalog Organization
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the
 * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
 * website http://www.extensiblecatalog.org/.
 *
 */

package xc.mst.services.transformation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.jdom.Element;

import xc.mst.bo.provider.Format;
import xc.mst.bo.record.AggregateXCRecord;
import xc.mst.bo.record.InputRecord;
import xc.mst.bo.record.Marc001_003Holder;
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

    // Keep track of records we wish to edit payload only (and not their predecessor linkage)
    protected Map<Long, Boolean> unchangedPredecessors = new HashMap<Long, Boolean>();
    
    // XC's org code
    public static final String XC_SOURCE_OF_MARC_ORG = "NyRoXCO";
   
    protected Format xcFormat = null;

    protected TransformationDAO transformationDAO = null;

    protected int inputBibs = 0;
    protected int inputHoldings = 0;

    protected int outputWorks = 0;
    protected int outputExpressions = 0;
    protected int outputManifestations = 0;

    public static final long COMMIT_EVERY = 10000L;
    protected long lastCommitCount = 0;

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
        
        TimingLogger.reset();
        inputBibs = getRepository().getPersistentPropertyAsInt("inputBibs", 0);
        inputHoldings = getRepository().getPersistentPropertyAsInt("inputHoldings", 0);
    }
    
    protected Long getRecordId4BibProcessed(String orgCode, String s) {
    	List<Long> r = getTransformationDAO().getRecordId4BibProcessed(new Marc001_003Holder(s, orgCode));
    	return (r == null || r.size() < 1) ? null : r.get(0);

    }
    
    protected Marc001_003Holder getHoldingMarcId4RecordIdProcessed(long l) {
    	List<Marc001_003Holder> r = getTransformationDAO().getHoldingMarcId4RecordIdProcessed(l);
    	return (r == null || r.size() < 1) ? null : r.get(0);
    }
    
    
    protected void addRecordId4BibProcessed(String orgCode, String s, Long l) {
    	getTransformationDAO().addRecordId4BibProcessed(new Marc001_003Holder(s, orgCode), l);
    }

    protected void addRecordId4HoldingProcessed(String orgCode, String s, Long l) {
    	getTransformationDAO().addRecordId4HoldingProcessed(new Marc001_003Holder(s, orgCode), l);
    }

    protected void removeRecordId4BibProcessed(Long l) {
    	getTransformationDAO().removeRecordId4BibProcessed(l);
    }

    protected void removeRecordId4HoldingProcessed(Long l) {
    	getTransformationDAO().removeRecordId4HoldingProcessed(l);
    }

    protected List<Long> getManifestationId4BibYet2Arrive(String orgCode, String s) {
    	return getTransformationDAO().getManifestationId4BibYet2Arrive(new Marc001_003Holder(s, orgCode));
    }

    protected void addManifestationId4BibYet2Arrive(String orgCode, String s, Long l) {
    	getTransformationDAO().addManifestationId4BibYet2Arrive(new Marc001_003Holder(s, orgCode), l);
    }

    protected void removeManifestationId4BibYet2Arrive(String orgCode, String s, Long l) {
    	getTransformationDAO().removeManifestationId4BibYet2Arrive(new Marc001_003Holder(s, orgCode), l);
    	
    }
 
    protected List<String> getHoldingsForBib(String orgCode, String bib_id) {
    	return getTransformationDAO().getHoldingsForBib(new Marc001_003Holder(bib_id, orgCode));
    }
    
    protected List<String> getBibsForHolding(String orgCode, String hold_id) {
    	return getTransformationDAO().getBibsForHoldings(new Marc001_003Holder(hold_id, orgCode));
    }

    protected void addBibsforHolding(String orgCode, String holding_id, List<String> bib_ids) {
        for (String bib_id : bib_ids) {
        	addBibforHolding(orgCode, holding_id, bib_id);
        }    	
    }
    	
    protected void addBibforHolding(String orgCode, String holding_id, String bib_id) {  
    	getTransformationDAO().addBibforHolding(orgCode, holding_id, bib_id);
    }

    protected void removeBibsForHolding(String orgCode, String holding_id) {
    	getTransformationDAO().removeBibsForHoldings(orgCode, holding_id);
    }

    protected void removeBibForHolding(String orgCode, String holding_id, String bib_id) {
    	getTransformationDAO().removeBibForHolding(orgCode, holding_id, bib_id);
    }

    @Override
    protected boolean commitIfNecessary(boolean force, long processedRecordsCount) {
    	boolean forceIt = force;
    	
    	if (! forceIt) {
    		if (processedRecordCount - lastCommitCount >= COMMIT_EVERY) {
    			forceIt = true;
    		}
    	}
    	    	
    	if (!forceIt) {
            return super.commitIfNecessary(force, 0);
        }
    	
		lastCommitCount = processedRecordsCount;

		try {
            TimingLogger.start("TransformationDAO.endBatch");

            unchangedPredecessors.clear();
            
            TimingLogger.start("TransformationDAO.non-generic");
            super.commitIfNecessary(true, 0);
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
                	for (OutputRecord or : record.getSuccessors()) {
                        or.setStatus(Record.DELETED);
                        results.add(or);
                        Record r = getRepository().getRecord(or.getId());
                        String type = getXCRecordService().getType(r);
                        or.setType(type);
                        if (or.getType().equals("manifestation")) {
                        	
                        	// remove this bib from our map now, so that we don't re-use it later (it's not usable anymore)
                        	removeRecordId4BibProcessed(or.getId());

                        	// Are there any holdings records which reference this deleted manifestation?
                        	LOG.info("Deleting a bib (incoming record_id: " + record.getId() + ", successor_id: " + or.getId() + ")");
                        	List<Long> xc_holdings_ids = getRepository().getLinkedRecordIds(or.getId());
                        	for (long xc_holding_id : xc_holdings_ids) {
	        					//
	        					// we need to remove the linkages from the orphaned holding record to this manifestation
	        					//
	        					List<Long> xc_manifestation_ids_orphan = getRepository().getLinkedToRecordIds(xc_holding_id);
	        					for (long xc_manifestation_id_orphan : xc_manifestation_ids_orphan) {
	        						if (xc_manifestation_id_orphan == or.getId()) {
	        							LOG.info("Sanity check. Good. We see that the orphan references this deleted manifestation as expected.");
	        						}
                            		// keep holdings -> manifestation relationship up-to-date
    	        					getRepository().removeLink(xc_holding_id, xc_manifestation_id_orphan);	    	        					
	        					}
 
                        		LOG.info("\ttrying to fix orphaned holding record_id: " + xc_holding_id);                        		
                        		// Try to find another manifestation to link to, now that this one is being deleted.
                        		Marc001_003Holder holding_id = getHoldingMarcId4RecordIdProcessed(xc_holding_id);
                        		if (holding_id == null) {
                        			LOG.error("Whoa!! In delete MANIFESTATION. Couldn't find holding marc id for xc record id of " + xc_holding_id);
                        			continue;
                        		}
                        		LOG.info("\tfound holding_id: " + holding_id);
                        		
	        					List<Long> new_manifestation_ids = new ArrayList<Long>(); // keep all the others
                        		List<String> bib_ids = getBibsForHolding(holding_id.get003(), holding_id.get001());
                        		boolean held = false; // is the holding record referring to this bib 'held' (orphan)?
                    			for (String bib_id : bib_ids) {
                    				LOG.info("\tfound bib_id: " + bib_id + " for this holding_id: " + holding_id);
                           			Long new_manifestation_id = getRecordId4BibProcessed(holding_id.get003(), bib_id);
                           			if (new_manifestation_id == null) {
                           				held = true;
                        				new_manifestation_id = getRepositoryDAO().getNextIdAndIncr();
                           				LOG.info("\tcouldn't find a manifestation for this bib_id; using placeholder id: " + new_manifestation_id);
                           				new_manifestation_ids.add(new_manifestation_id);
                        				addManifestationId4BibYet2Arrive(
                        						holding_id.get003(), bib_id, new_manifestation_id);
                           			} else {
                           				LOG.info("\tfound a manifestation for this bib_id id: " + new_manifestation_id);
                           				new_manifestation_ids.add(new_manifestation_id);
                    				}
                    			}
                        		        
                    			Record fixed = null;
                    			if (new_manifestation_ids.size() > 0) {
                    				// we found a match. add these new references
                    				fixed = fixManifestationId(xc_holding_id, null, new_manifestation_ids);
                            	    if (fixed != null) {
                            	    	results.add(fixed);                        		
                            	    }
                    			}

                    			if (held) {
                    				// we're missing bib(s). we need to set this holding record as "held" (waiting for manifestation)
                            	    if (fixed != null) {
                            	    	LOG.info("\tcouldn't find (at least) one manifestation for this holding record; setting it to held");
                            	    	fixed.setStatus(Record.HELD);
                            	    }
                    			}
                        	}

                        } else if (or.getType().equals("holdings")) {
                        	// remove the holding -> bib relationship in our map
                        	Marc001_003Holder holding_id = getHoldingMarcId4RecordIdProcessed(or.getId());
                        	if (holding_id == null) {
                    			LOG.error("Whoa!! In delete HOLDINGS. Couldn't find holding marc id for xc record id of " + or.getId());
                        	} else {
                        		removeBibsForHolding(holding_id.get003(), holding_id.get001());
                        	}
                        	removeRecordId4HoldingProcessed(or.getId());   
                        	
                        	List<Long> xc_manifestation_ids = getRepository().getLinkedToRecordIds(or.getId());
                        	for (long xc_manifestation_id : xc_manifestation_ids) {
                        		// keep holding -> manifest relationship up-to-date
                        		getRepository().removeLink(or.getId(), xc_manifestation_id);

                        	}

                        }   
                                                
                }
                                
            } else {
            	
                record.setMode(Record.STRING_MODE);

                String sourceOfRecords = null;
                if (config.getPropertyAsInt("SourceOf9XXFields", 0) == 1)
                	sourceOfRecords = XC_SOURCE_OF_MARC_ORG;
                
                SaxMarcXmlRecord originalRecord = new SaxMarcXmlRecord(record.getOaiXml(), sourceOfRecords);

                // Get the ORG code from the 003 or 035 field
                orgCode = originalRecord.getOrgCode();

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
                
                boolean isNew = true;
                long editedHoldingsRecord = -1; // useful placeholder - there is always only 1 incoming holding record

                // this means it's an edit, i.e., not a new record
                if (record.getSuccessors() != null && record.getSuccessors().size() > 0) {
                	isNew = false;
                    for (OutputRecord or : record.getSuccessors()) {
                        Record succ = getRepository().getRecord(or.getId());
                        // ignore deleted successors
                        // they were deleted, so we need to ignore them forever
                        // (new successors and OAI IDs get generated whenever a deleted record later gets re-activated)
                        if (! succ.getDeleted()) {
	                        String type = getXCRecordService().getType(succ);
	                        or.setType(type);
	                        if (AggregateXCRecord.HOLDINGS.equals(type)) {
	                            ar.getPreviousHoldingIds().add(or.getId());
	                            editedHoldingsRecord = or.getId();
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
                if (isBib) {
                    Long manifestationId = null;

                    //
                    // This for-loop is for the sole purpose of handling "held" holding records:
                    //
                	// A merged (deduped) record coming from MarcAggregation Service (MAS) does not contain a 001 field.
                    // Instead, it contains multiple 035$a fields.
                	// In the merged record case, we need to treat its (multiple) 035s as if they were 001s (issue mst-473).
                	// The getBib001_or_035s() method returns either a single 001 (if one exists), or else multiple 035s.
                    List<Marc001_003Holder> _001_003s = originalRecord.getBib001_or_035s();
                	for (Marc001_003Holder this_001_003 : _001_003s) {
                		
                        if (StringUtils.isEmpty(this_001_003.get003())) {
                            addMessage(record, 106, RecordMessage.ERROR);
                            return null;
                        }
                	
                		// Does a "held" holding record exist for this bib (i.e., is it waiting for us)? If so, we need to activate it.
                        List<Long> this_manifestationIds = getManifestationId4BibYet2Arrive(
                        		this_001_003.get003(), this_001_003.get001());
                        
                        if (this_manifestationIds.size() > 0) {
                        	
	                        for (Long this_manifestationId : this_manifestationIds) {
	                        	// Reminder: if a holding record arrived before its referenced bib (this is called a "held" holding record),
	                        	// a manifestationId for this bib was generated ahead of time (when the holdings record arrived).
	
	                        	// However: it's possible that two or more "held" holding records have linked to this bib.
	                        	// This means that two or more manifestationIds have been created and designated for this bib.
	                        	// But since this bib may only have one manifestationId, we need to choose one to represent them all.
	                        	// Let's use the first match as the chosen "source" manifestationId.
	                        	if (manifestationId == null) {
	                        		manifestationId = this_manifestationId;
	                        	}
	                       		
	                        	// Remove the manifestationId that we just matched on, which may or may not be the "source"
	                            removeManifestationId4BibYet2Arrive(
	                                    this_001_003.get003(), this_001_003.get001(), this_manifestationId);
	                        	
	                            // This manifestationId needs to be represented by the above-chosen "source"
	                            if (manifestationId != this_manifestationId) {
	                            	
	                            	// fix the "stale" manifestationId
	                            	List<Long> stale_holding_ids = getRepository().getLinkedRecordIds(this_manifestationId);

	                            	for (Long stale_holding_id : stale_holding_ids) {
		                            	// We need to edit this holdings output record by changing its stale manifestationHeld Id to the chosen "source" manifestationId.
	                            	    Record fixed = fixManifestationId(stale_holding_id, this_manifestationId, manifestationId);
	                            	    if (fixed != null) results.add(fixed);
	                            	}
	                            	
	                            	// remove "stale" manifestationId here, too
	                            	removeRecordId4BibProcessed(this_manifestationId);
	                            }
	                		}
	                        
	                	}
                        
                	}
                			                	                        
                	// No holding records are waiting for us...
                    if (manifestationId == null) {
                        if (ar.getPreviousManifestationId() != null) {
                        	// it's an update
                            manifestationId = ar.getPreviousManifestationId();
                        } else {
                        	// it's a new record
                            manifestationId = getRepositoryDAO().getNextIdAndIncr();
                        }
                        
                    }

                    // store the associated 003/001 (or, in the case of a merged MAS record, all of its 003/035s: issue mst-473)
                    for (Marc001_003Holder this_001_003 : _001_003s) {
                        addRecordId4BibProcessed(
                        		this_001_003.get003(), this_001_003.get001(), manifestationId);
                	}
                                            
                    List<OutputRecord> bibRecords = getXCRecordService().getSplitXCRecordXML(
                            getRepository(), ar, manifestationId, 0L /* ignored */);
                    if (bibRecords != null) {
                        results.addAll(bibRecords);
                    }
                } else if (isHolding) {
                    char status = Record.ACTIVE;
                    List<Long> manifestationIds = new ArrayList<Long>();
                    if (ar.getReferencedBibs() == null) {
                        LOG.error("ar.getReferencedBibs() == null!!!");
                    } else {

                        if (StringUtils.isEmpty(orgCode)) {
                            addMessage(record, 106, RecordMessage.ERROR);
                            return null;
                        } else {
	                        // we need to reset the holdings -> bibs/manifestations maps, if it's an update
	                        if (! isNew) {
	                        	removeBibsForHolding(orgCode, originalRecord.getControlField(1));
	                        	if (editedHoldingsRecord > -1) {
		                        	List<Long> xc_manifestation_ids = getRepository().getLinkedToRecordIds(editedHoldingsRecord);
		                        	for (long xc_manifestation_id : xc_manifestation_ids) {
		                        		// keep holding -> manifest relationship up-to-date
		                        		getRepository().removeLink(editedHoldingsRecord, xc_manifestation_id);
	
		                        	}
	                        	}
	                        }
	                    	addBibsforHolding(orgCode, originalRecord.getControlField(1), new ArrayList<String>(ar.getReferencedBibs()));
	                    	
	                        for (String ref001 : ar.getReferencedBibs()) {
	                            Long manifestationId = getRecordId4BibProcessed(
	                                    orgCode, ref001);
	
	                            LOG.debug("input " + record.getId() + "manifestationId: " + manifestationId);
	
	                            if (manifestationId == null) {
	                                // This bib doesn't exist yet, therefore we will set this holdings record's state to "held."
	                                status = Record.HELD;
	                            	
	                            	// At this point, we haven't encountered the referenced (linked-to) bib yet.
	                            	// Let's see if any holdings records before us have already run into this same situation,
	                            	// and, if so, we should link to the same manifestationId.
	                                List<Long> waitingManifestationIds = getManifestationId4BibYet2Arrive(
	                                        orgCode, ref001);
	                                
	                                if (waitingManifestationIds.size() < 1) {
	                                    // So, we are the first record waiting to be linked to this bib. Therefore we will
	                                	// choose the manifestationId for this bib now (so that we may link to it now,
	                                	// and create the "held" holdings record now).
	                                	
	                                	// NOTE: In the case of a merged MAS bib,
	                                	// it's quite possible that we will end up with multiple manifestationIds for the same
	                                	// bib. We won't know this until the merged bib actually arrives.
	                                	// When it does arrive, we will need to handle it then.
	                                    manifestationId = getRepositoryDAO().getNextIdAndIncr();
	                                    
	                                    // Store this manifestationId so that future holdings records may also link to this manifestationId.
	                                    addManifestationId4BibYet2Arrive(
	                                            orgCode, ref001, manifestationId);
	                                    
	                                    waitingManifestationIds.add(manifestationId);
	                                }
                                	for (Long this_manifestationId : waitingManifestationIds) {	                                    
	                                	manifestationIds.add(this_manifestationId);
	                                }
                        
	                            } else {
	                            	manifestationIds.add(manifestationId);
	                            }
	                        }
	                        
	                        List<OutputRecord> holdingsRecords = getXCRecordService().getSplitXCRecordXMLForHoldingRecord(
	                                getRepository(), ar, manifestationIds, 0L /* ignored */);
	
	                        if (holdingsRecords != null) {
	                            for (OutputRecord r : holdingsRecords) {
	                                r.setStatus(status);
	                                results.add(r);
	                                if (isNew) {
	                                    LOG.info("Adding " + (isNew ? "new" : "existing") + " HOLDING record (ID: " + r.getId() + ")");
	                                    addRecordId4HoldingProcessed(
	                                    		orgCode, originalRecord.getControlField(1), r.getId());
	                                }
	                            }
	                        } else {
	                            LOG.debug("holdingsRecords == null");
	                        }
	                    }
	                }
	                // update service accordingly w/ new record counts
	            }
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

    // A safe way to retrieve a record, whether it has already been persisted or not.
    protected Record getRecord(Long recordId) {
    	// We may need to access in-memory (not yet persisted) records.
    	// MST doesn't provide a safe framework for manipulating in-memory objects; therefore, we will persist all records first!
    	if (getRepositoryDAO().haveUnpersistedRecord(recordId)) {
            super.commitIfNecessary(true, 0);
    	}
    	Record r = getRepository().getRecord(recordId);
    	return r;
    }
    
    protected Record fixManifestationId(Long recordId, Long staleManifestationId, Long newManifestationId) {
    	List<Long> l = new ArrayList<Long>();
    	l.add(newManifestationId);
    	return fixManifestationId(recordId, staleManifestationId, l);
    }
    
    @SuppressWarnings("unchecked")
    // if staleManifestationId is null, we reset all manifestationIds
	protected Record fixManifestationId(Long recordId, Long staleManifestationId, List<Long> newManifestationIds) {
       
    	String staleUplink = "";
    	if (staleManifestationId != null) staleUplink = getRecordService().getOaiIdentifier(
    			staleManifestationId, getMetadataService().getService());
    	
    	List<String> currentUplinks = new ArrayList<String>();
    	
    	Record r = getRecord(recordId);
		if (r != null) {
			r.setMode(Record.JDOM_MODE);
			Element root = r.getOaiXmlEl();
			Element holdingsElement = null;
	        List<Element> elements = root.getChildren();
	        for (Element element : elements) {
	            String frbrLevel = element.getAttributeValue("type");
	            if (frbrLevel.equals("holdings")) {
	            	if (holdingsElement == null) {
	            		holdingsElement = element;
	            	}
	            	List<Element> heldEls = element.getChildren("manifestationHeld", element.getNamespace());
	            	List<Element> deleteThese = new ArrayList<Element>();
	            	for (Element heldEl : heldEls) {
	            		final String uplink = heldEl.getText();
	            		if(staleManifestationId==null || uplink.equals(staleUplink)) {
	            			deleteThese.add(heldEl);
	            		} else {
	            			currentUplinks.add(uplink);
	            		}
	            	}
	            	for (Element deleteThis : deleteThese) {
        				// delete
        				LOG.info("Deleting stale manifestation link: " + deleteThis.getText());
        				heldEls.remove(deleteThis);	            		
	            	}
	            }
	        }
            if (holdingsElement == null) {
            	LOG.error("Couldn't find holdings element in fixManifestationId()");
            	return null;
            }

	        // need to keep repo's links up-to-date too
			if (staleManifestationId!=null) getRepository().removeLink(recordId, staleManifestationId);
			
			for (Long newManifestationId : newManifestationIds) {
		    	String newUplink = getRecordService().getOaiIdentifier(
		    			newManifestationId, getMetadataService().getService());
		    	
				// Make sure we do not add any duplicate references
		    	if (currentUplinks.contains(newUplink)) continue;
		    	
		        Element linkManifestation = new Element("manifestationHeld",
	                    AggregateXCRecord.XC_NAMESPACE);
	            linkManifestation.setText(newUplink);
	            
	            holdingsElement.addContent(linkManifestation.detach());
				LOG.info("Record id:" + r.getId() + " new uplink via fixManifestationId(): " + newUplink);

				getRepository().addLink(recordId, newManifestationId);
	        }
	        
	        r.setUpdatedAt(null); // we need this to be set when repo is persisted.
	        unchangedPredecessors.put(recordId, true);
	        return r;
		}
		return null;
	}
    
    
    /**
     * At times, we will be editing records which already exist.
     * In these cases, we will not need to mess with predecessor linkage (i.e., 
     * we will keep the same values).
     * We will mark these records in the unchangedPredecessors collection, so that later
     * on, the addPredecessor() method will know which records to ignore.
     */
    @Override
    protected void addPredecessor(Record in, Record out) {
    	if (! unchangedPredecessors.containsKey(out.getId())) {
        	super.addPredecessor(in, out);    	    		
    	}
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

        // TODO: The following process does not work correctly.
        //transformedRecord = process852(originalRecord, transformedRecord, record);
        
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
        
        // TODO: The following process does not work correctly.
        //transformedRecord = process945(originalRecord, transformedRecord);
        
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
