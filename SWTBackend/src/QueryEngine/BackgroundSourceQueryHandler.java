package QueryEngine;

import java.util.List;

import org.apache.jena.rdf.model.Model;

import NEREngine.NamedEntity.EntityType;

public class BackgroundSourceQueryHandler extends Thread {
	private Source s;
	private EntityType et;
	private List<String> entities;
	private Model m;
	private List<String> cacheRef;
	private String filter;


	public BackgroundSourceQueryHandler(ThreadGroup group, Source s, EntityType et ,List<String> entities, List<String> cacheRef, String filter) {
		super(group,(et + "_" + entities));
		this.s = s;
		this.et = et;
		this.entities = entities;		
		this.m = null;
		this.cacheRef = cacheRef;
		this.filter = filter;	
	}

	
	public void run(){
		switch (s) {
		case DBPedia:
			m = new QueryDBPedia(et, entities, filter).getModel(); 
			break;
		default:
			break;
		}
		
	}
	
	public Model getResultModel(){
		return m;
	}
	
	public List<String> getCacheRef(){
		return cacheRef;
	}
	
	public Source getSource(){
		return s;
	}
	
	public enum Source {
	    DBPedia
	}

}
