package xc.mst.services.transformation.test;

import gnu.trove.TLongLongHashMap;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

import xc.mst.service.impl.test.BaseInternalTest;
import xc.mst.services.transformation.TransformationService;
import xc.mst.services.transformation.dao.TransformationDAO;

public class TransformationDaoTest extends BaseInternalTest {
	
	private final static Logger LOG = Logger.getLogger(TransformationDaoTest.class);
	
	@BeforeSuite
	@Override
	public void startup() {
		/*
		super.startup();
		try {
			repositoryDAO.deleteSchema(getServiceName());
		} catch (Throwable t) {
		}
		try {
			getServicesService().addNewService(getServiceName());
		} catch (Throwable t) {
			LOG.error("", t);
		}
		*/
	}

	@Test
	public void testPersistBibMaps() {
		LOG.debug("getClass().getClassLoader(): "+getClass().getClassLoader());
		ClassLoader parent = getClass().getClassLoader().getParent();
		ClassLoader previousParent = null;
		while (parent != null && (previousParent == null || previousParent != parent )) {
			LOG.debug("parent: "+parent);
			previousParent = parent;
			parent = getClass().getClassLoader().getParent();	
		}
		
		TransformationDAO transformationDAO = ((TransformationService)ac.getBean("MetadataService")).getTransformationDAO();
		TLongLongHashMap bibsProcessedLongId = new TLongLongHashMap();
		Map<String, Long> bibsProcessedStringId = new HashMap<String, Long>();
		TLongLongHashMap bibsYet2ArriveLongId = new TLongLongHashMap();
		Map<String, Long> bibsYet2ArriveStringId = new HashMap<String, Long>();
		
		bibsProcessedLongId.put(1, 2);
		bibsProcessedLongId.put(3, 4);
		transformationDAO.persistBibMaps(bibsProcessedLongId, bibsProcessedStringId, bibsYet2ArriveLongId, bibsYet2ArriveStringId);
	}
	
}
