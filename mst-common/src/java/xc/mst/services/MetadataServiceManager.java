package xc.mst.services;

import java.util.List;

import xc.mst.bo.provider.Format;
import xc.mst.bo.provider.Set;
import xc.mst.manager.BaseManager;
import xc.mst.repo.Repository;
import xc.mst.scheduling.WorkDelegate;

public class MetadataServiceManager extends BaseManager implements WorkDelegate {
	
	protected MetadataService metadataService = null;
	protected Repository incomingRepository = null;
	protected List<Format> triggeringFormats = null;
	protected List<Set> triggeringSets = null;
	protected Set outputSet = null;

	public Set getOutputSet() {
		return outputSet;
	}

	public void setOutputSet(Set outputSet) {
		this.outputSet = outputSet;
	}

	public MetadataService getMetadataService() {
		return metadataService;
	}

	public void setMetadataService(MetadataService metadataService) {
		this.metadataService = metadataService;
	}
	
	public Repository getIncomingRepository() {
		return incomingRepository;
	}

	public void setIncomingRepository(Repository incomingRepository) {
		this.incomingRepository = incomingRepository;
	}

	public void cancel() {
		metadataService.cancel();
	}
	
	public List<Format> getTriggeringFormats() {
		return triggeringFormats;
	}

	public void setTriggeringFormats(List<Format> triggeringFormats) {
		this.triggeringFormats = triggeringFormats;
	}
	
	public List<Set> getTriggeringSets() {
		return triggeringSets;
	}

	public void setTriggeringSets(List<Set> sets) {
		this.triggeringSets = sets;
	}

	public boolean doSomeWork() {
		for (Format f : triggeringFormats) {
			for (Set s : triggeringSets) {
				metadataService.process(incomingRepository, f, s, outputSet);
			}
		}
		return false;
	}

	public void setup() {}
	public void finish() {
		metadataService.finish();
	}

	public String getDetailedStatus() {
		return null;
	}

	public String getName() {
		return metadataService.getService().getName();
	}

	public int getRecordsProcessed() {
		return metadataService.getProcessedRecordCount();
	}

	public int getTotalRecords() {
		return metadataService.getTotalRecordCount();
	}

	public void pause() {
		metadataService.pause();
	}

	public void resume() {
		metadataService.resume();
	}

}