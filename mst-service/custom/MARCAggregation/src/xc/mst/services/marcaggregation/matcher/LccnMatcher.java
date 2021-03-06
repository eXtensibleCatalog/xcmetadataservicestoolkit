/**
  * Copyright (c) 2011 eXtensible Catalog Organization
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the
  * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
  * website http://www.extensiblecatalog.org/.
  *
  * @author Benjamin D. Anderson
  *
  */
package xc.mst.services.marcaggregation.matcher;

import gnu.trove.TLongLongHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import xc.mst.bo.record.InputRecord;
import xc.mst.bo.record.RecordMessage;
import xc.mst.bo.record.SaxMarcXmlRecord;
import xc.mst.bo.record.marc.Field;
import xc.mst.services.marcaggregation.MarcAggregationService;
import xc.mst.services.marcaggregation.dao.MarcAggregationServiceDAO;
import xc.mst.utils.Util;

/**
 *
 * The Library of Congress Control Number corresponds to the
 * <a href="http://www.loc.gov/marc/bibliographic/bd010.html">MARC 010 field</a>
 *
 * 010$a
 *
 * The examples at the above site show alphanumeric data is possible, correct?   Yes - JBB
           (I have this note in my source though, is it correct ?) :
             //  find the first numeric value and return it
        //    This was determined by Jennifer in a phone conversation.
           If so, must modify our schema accordingly. (string field in addition to numeric.)
           Example: 010      ##$anuc76039265#
           Example: 010     ##$a##2001627090
    If no alphabetic prefix is present, then there should be either 2 or 3 blanks, as in the example above.

    Note, 010 does not repeat, nor does $a, so only 1 of these max per record.
 *
 * @author Benjamin D. Anderson
 * @author John Brand
 *
 */
public class LccnMatcher extends FieldMatcherService {

    //private boolean debug = false;

    private static final Logger LOG = Logger.getLogger(LccnMatcher.class);

    // multiple records might have the same normalized 010$a, this would be an indication of a match
    protected Map<Long, List<Long>> lccn2inputIds = new HashMap<Long, List<Long>>();

    // you can have exactly 1 010$a fields within a record  (1 010, w/1 $a)
    protected TLongLongHashMap inputId2lccn = new TLongLongHashMap();
    protected TLongLongHashMap inputId2lccn_unpersisted = new TLongLongHashMap();
    
    private boolean keepAllCached = false; //true;
    
    MarcAggregationService mas = null;
    
	private MarcAggregationService getMAS() {
		if (mas == null) {
			mas = (MarcAggregationService)config.getBean("MarcAggregationService");
		}
		return mas;
	}
    
    //don't need to save this
    //protected Map<Long, String> inputId2lccnStr = new HashMap<Long, String>();

    // am not sure how much I can count on the int starting at char 3 (0,1,2,3)
    // there is a prefix or spaces before the int, and the prefix can run right
    // to the int without a space between.
    // <== update - prefix not always there.
    //
    // http://www.loc.gov/marc/bibliographic/bd010.html
    // find the first numeric value and return it
    // This was determined by Jennifer in a phone conversation.
    //
    //  Note that I have seen prefix and suffix data, i.e.:
    //   <marc:datafield tag="010" ind1=" " ind2=" ">
    //   <marc:subfield code="a">m  61000295 /M/r86</marc:subfield>
    //
    //  Seems like trimming 1st 3 chars off will work,
    //  then tokenize around spaces, return the numeric portion that
    //  remains (or could test 1st. to verify it is numeric)
    //
    //  on error use the somewhat old-fashioned mechanism of -1 returned, which is not valid unique id
    //
    //TODO
    //change the matching algorithm for Aggregation to ignore a forward slash and any characters that follow it in matching 010 fields
    //
    public static long getUniqueId(String s) {
        String stripped=null;
        long strippedL=0l;
        StringTokenizer st = new StringTokenizer(s);
        String candidate;
        if (st.hasMoreTokens()) {
            candidate = st.nextToken();
        }
        else {
            LOG.error("** Problem with 010$a, empty, original="+s);
            return -1l;
        }
        if (StringUtils.isNotEmpty(candidate) && StringUtils.isNumeric(candidate)) {
            return Long.parseLong(candidate);
        }
        else if (candidate.toCharArray().length <=3) {
        	if (st.hasMoreTokens()) {
                candidate = st.nextToken();
            }
            else {
                LOG.error("** Problem with 010$a, length<=3, original="+s);
                return -1l;
            }
            if (StringUtils.isNotEmpty(candidate) && StringUtils.isNumeric(candidate)) {
                return Long.parseLong(candidate);
            }
            else {
                try {
                    stripped = candidate.replaceAll("[^\\d]", "");
                    strippedL = Long.parseLong(stripped);
                    LOG.debug("numericID:"+strippedL);
                }catch(NumberFormatException e) {
                    LOG.error("** Problem with stripped string, not numeric, original="+s+" all_data="+ " stripped="+stripped);
                    stripped=null;
                }
                if (stripped == null) {
                    return -1l;
                }
                return strippedL;
            }
        }
        else {  // one long str including prefix.
            try {
                stripped = candidate.replaceAll("[^\\d]", "");
                strippedL = Long.parseLong(stripped);
                LOG.debug("numericID:"+strippedL);
            }catch(NumberFormatException e) {
                LOG.error("** Problem with stripped string, not numeric, original="+s+" all_data="+ " stripped="+stripped);
                stripped=null;
            }
            if (stripped == null) {
                return -1l;
            }
            return strippedL;
        }
    }

    @Override
    public List<Long> getMatchingInputIds(SaxMarcXmlRecord ir) {
        MarcAggregationServiceDAO masDao = getMAS().getMarcAggregationServiceDAO();

        ArrayList<Long> results = new ArrayList<Long>();
        List<Field> fields = ir.getDataFields(10);

        final Long id = new Long(ir.recordId);
        for (Field field: fields) {
            List<String> subfields = SaxMarcXmlRecord.getSubfieldOfField(field, 'a');
                
            // there will be only 1 subfield, but this won't hurt...
            for (String subfield : subfields) {
                Long goods = new Long(getUniqueId(subfield));
                if (goods <= 0L) continue; // we don't accept <= 0
                
                // look in memory first
            	List<Long> m = lccn2inputIds.get(goods);
                if (m != null && m.size() > 0) {
                    results.addAll(m);
                    if (results.contains(id)) {
                        results.remove(id);
                    }
                }
                // also, look in the database
                //mysql -u root --password=root -D xc_marcaggregation -e 'select input_record_id  from matchpoints_010a where string_id = "24094664" '
                List<Long> records = masDao.getMatchingRecords(MarcAggregationServiceDAO.matchpoints_010a_table, MarcAggregationServiceDAO.input_record_id_field,MarcAggregationServiceDAO.numeric_id_field,goods);
                LOG.debug("LCCN, DAO, getMatching records for "+goods+", numResults="+records.size());
                for (Long record: records) {
                    if (!record.equals(id)) {
                        if (!results.contains(record)) {
                            results.add(record);
                            LOG.debug("**LCCN, DAO,  record id: "+record +" matches id "+id);
                        }
                    }
                }
            }
        }
        LOG.debug("getMatchinginputIds, irId="+ ir.recordId+" results.size="+results.size());
        return results;
    }

    /**
     * when a record is updated/deleted, need to use this to
     */
    @Override
    public void removeRecordFromMatcher(InputRecord ir) {
        Long id   = new Long(ir.getId());
        Long lccn = inputId2lccn.get(id);
        List<Long> inputIds = null;

        if (lccn != null) {
            inputIds = lccn2inputIds.get(lccn);
        }
        if (inputIds != null) {
            inputIds.remove(id);
            if (inputIds.size() > 0) {
                lccn2inputIds.put(lccn, inputIds);
            }
            else {
                lccn2inputIds.remove(lccn);
            }
        }
        inputId2lccn.remove(id);
        if (MarcAggregationService.hasIntermediatePersistence) {
            inputId2lccn_unpersisted.remove(id);
        }

        // keep database in sync.  Don't worry about the one-off performance hit...yet.
        MarcAggregationService s = getMAS();
        s.getMarcAggregationServiceDAO().deleteMergeRow(MarcAggregationServiceDAO.matchpoints_010a_table, id);
    }

    @Override
    public void addRecordToMatcher(SaxMarcXmlRecord r, InputRecord ir) {
        List<Field> fields = r.getDataFields(10);

        for (Field field: fields) {
            List<String> subfields = SaxMarcXmlRecord.getSubfieldOfField(field, 'a');

            for (String subfield : subfields) {
                Long id = new Long(r.recordId);
                Long goods = new Long(getUniqueId(subfield));
                if (goods < 1l) {   // then we're not successful in parsing, i.e. bad data. (we're also not accepting the value 0, which seems like another invalid value)
                    continue;
                }
                Long oldGoods = inputId2lccn.get(id);
                if (oldGoods == null) {
                    inputId2lccn.put(id, goods);
                    if (MarcAggregationService.hasIntermediatePersistence) {
                        inputId2lccn_unpersisted.put(id, goods);
                    }
                } else {
                    if (!goods.equals(oldGoods)) {
                        inputId2lccn.put(id, goods);
                        if (MarcAggregationService.hasIntermediatePersistence) {
                            inputId2lccn_unpersisted.put(id, goods);
                        }
                        LOG.debug("we have already seen a different 010 entry ("+oldGoods+") for recordId: "+r.recordId+ " this 010: "+goods);
                    }
                    else {
                        LOG.debug("we have already seen "+ goods +" for recordId: "+r.recordId);
                    }
                }

                List<Long> idsList = lccn2inputIds.get(goods);
                if (idsList == null || idsList.size() == 0) {
                    idsList = new ArrayList<Long>();
                    idsList.add(id);
                    lccn2inputIds.put(goods, idsList);
                }
                else if (!idsList.contains(id)){
                    idsList.add(id);
                    lccn2inputIds.put(goods, idsList);
                }
                else {  //error?
                    LOG.debug("we have already seen "+ id +" for recordId: "+r.recordId);
                }
            }
        }
    }
    
    public boolean matchpointsHaveChanged(SaxMarcXmlRecord r, InputRecord ir) {
    	LOG.debug("LCCN matchpointsHaveChanged? ID: " + ir.getId());    	
    	TLongLongHashMap cachedListId2lccn = getMAS().getMarcAggregationServiceDAO().getLccnRecordsCache(Long.valueOf(ir.getId()));
    	
    	Long cachedId2lccn = null;
        if (cachedListId2lccn.contains(ir.getId())) {
        	cachedId2lccn = cachedListId2lccn.get(ir.getId());
        	LOG.debug("cachedId2lccn: " + cachedId2lccn);        	
    	}
        
        Long thisId2lccn = null;
        
        List<Field> fields = r.getDataFields(10);

        for (Field field: fields) {
            List<String> subfields = SaxMarcXmlRecord.getSubfieldOfField(field, 'a');

            for (String subfield : subfields) {
                Long goods = new Long(getUniqueId(subfield));
                if (goods < 1l) {   // then were not successful in parsing, i.e. bad data. (we're also not accepting the value 0, which seems like another invalid value)
                    continue;
                }
                LOG.debug("setting thisId2lccn: " + goods);                
                thisId2lccn = goods;
            }
        }
        
        if (cachedId2lccn == null) {
        	if (thisId2lccn == null) return false;
        	return true;
        }
        if (thisId2lccn == null) {
        	if (cachedId2lccn == null) return false;
        	return true;
        }
        
        LOG.error("gonna compare cachedId2lccn: " + cachedId2lccn + "  ...with... thisId2lccn: " + thisId2lccn);
                
        return (! cachedId2lccn.equals(thisId2lccn));        
    }


    /**
     * note, this will only get hit if this matcher is in use, via custom.properties, so no worries about
     * unnecessarily loading stuff into memory.
     */
    @Override
    public void load(boolean firstTime) {
    	// we will only keep all objects in-memory for the initial (large) load; otherwise, we need to consult database too
    	keepAllCached = firstTime;
    	
    	if (! keepAllCached) return;

        MarcAggregationServiceDAO masDao = getMAS().getMarcAggregationServiceDAO();
        inputId2lccn = masDao.getLccnRecordsCache();
        LOG.info("inputId2lccn loaded, size="+inputId2lccn.size());

        // now go from inputId2lccn to populate lccn2inputIds
        for (Long id: inputId2lccn.keys()) {
            Long goods = inputId2lccn.get(id);
            //LOG.debug("** id: "+id+" lccn = "+goods);
            List<Long> idsList = lccn2inputIds.get(goods);
            if (idsList == null || idsList.size() == 0) {
                idsList = new ArrayList<Long>();
                idsList.add(id);
                lccn2inputIds.put(goods, idsList);
            }
            else if (!idsList.contains(id)){
                idsList.add(id);
                lccn2inputIds.put(goods, idsList);
            }
        }
    }

    @Override
    // at commit time put stuff into db, but for this one, don't need to do it as often, because we are able to keep the data in memory.
    public void flush(boolean force) {
        if (force) {
            
        	MarcAggregationService s = getMAS();
              	
            if (MarcAggregationService.hasIntermediatePersistence) {
                s.getMarcAggregationServiceDAO().persistLongMatchpointMaps(inputId2lccn_unpersisted, MarcAggregationServiceDAO.matchpoints_010a_table, true);
                inputId2lccn_unpersisted.clear();
            }
            else {
                s.getMarcAggregationServiceDAO().persistLongMatchpointMaps(inputId2lccn, MarcAggregationServiceDAO.matchpoints_010a_table, true);
            }
            
        	// we persisted everything already; no need to keep in-memory objects too
            if (! keepAllCached) {
            	inputId2lccn.clear();
            	lccn2inputIds.clear();
            }
        }

    }

    /**
     * unused, if you do end up needing it - must make sure to get all values.  Are all in memory?
     */
    public Collection<Long> getRecordIdsInMatcher() {
        List<Long> results = new ArrayList<Long>();
        for (Long record: inputId2lccn.keys()) {
            results.add(record);
        }
        return results;
    }

    /**
     * For testing.
     * @return
     */
    public int getNumRecordIdsInMatcher() {
        //return inputId2lccn.size();
        MarcAggregationService s = getMAS();
        LOG.debug("** 010 matcher contains "+s.getMarcAggregationServiceDAO().getNumUniqueRecordIds(MarcAggregationServiceDAO.matchpoints_010a_table)+ " unique records in dB & "+inputId2lccn.size() +" records in mem.");
        return s.getMarcAggregationServiceDAO().getNumUniqueRecordIds(MarcAggregationServiceDAO.matchpoints_010a_table);
    }

    /**
     * For testing.
     * @return
     */
    public int getNumMatchPointsInMatcher() {
        //return lccn2inputIds.size();
        MarcAggregationService s = getMAS();
        LOG.debug("** 010 matcher contains "+s.getMarcAggregationServiceDAO().getNumUniqueNumericIds(MarcAggregationServiceDAO.matchpoints_010a_table)+ " unique strings in dB & "+lccn2inputIds.size() +" strs in mem.");
        return s.getMarcAggregationServiceDAO().getNumUniqueNumericIds(MarcAggregationServiceDAO.matchpoints_010a_table);
    }

}
