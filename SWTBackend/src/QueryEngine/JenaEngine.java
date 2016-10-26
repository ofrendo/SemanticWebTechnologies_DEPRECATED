/**
 * 
 */
package QueryEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.*;

import NEREngine.NamedEntity;
import NEREngine.NamedEntity.EntityType;


/** 
 * @author Sascha Ulbrich
 *
 */
public class JenaEngine implements QueryEngine {
	private QueryProperties qp;
	private static Model model;
	private static List<String> inCache;
	
	public JenaEngine() {
		initAvailableProperties();
		if(model == null){
			System.out.println("Load model");
			model = ModelFactory.createMemModelMaker().openModel("Local_Cache", false);
			System.out.println(model.size());
		}
		if(inCache == null){
			inCache = new ArrayList<String>();
		}
	}	
	
	/* (non-Javadoc)
	 * @see QueryEngine.QueryEngine#getAvailableProperties()
	 */
	@Override
	public QueryProperties getAvailableProperties() {
		return qp;
	}

	/* (non-Javadoc)
	 * @see QueryEngine.QueryEngine#getAvailableProperties(NEREngine.NamedEntity.EntityType)
	 */
	@Override
	public List<String> getAvailableProperties(EntityType type) {		
		return qp.get(type);
	}

	/* (non-Javadoc)
	 * @see QueryEngine.QueryEngine#queryEntityProperties(java.util.List)
	 * Query properties with full set of available properties
	 */
	@Override
	public void queryEntityProperties(List<NamedEntity> entities) {
		queryEntityProperties(entities, qp);
	}

	/* (non-Javadoc)
	 * @see QueryEngine.QueryEngine#queryEntityProperties(java.util.List, java.util.Properties)
	 * Query properties with custom set of properties
	 */
	@Override
	public void queryEntityProperties(List<NamedEntity> entities,
			QueryProperties props) {
		
		for (NamedEntity namedEntity : entities) {
			//TODO define return type
			System.out.println(queryEntity(namedEntity, props.get(namedEntity.getType())).toString());
		}

	}	
	
	private HashMap<String,HashMap<String, Integer>> queryEntity(NamedEntity entity, List<String> props){
		
		//Query Sources to build model
		handleSourceQueries(entity);
		
		
		//construct dictionary for properties
		Hashtable<String, String> propDic = prepareProperties(props);
		
		//Construct local query
		String lq = constructLocalQuery(entity, propDic);		
		
		//Execute Query
		return executeLocalQuery(lq,propDic);		
	}
	
	private void handleSourceQueries(NamedEntity entity) {
		String cacheRef = entity.getType() + "_" + entity.getName();
		
		if(!inCache.contains(cacheRef)){
			//Query source
			System.out.println("Query DBPedia");
			model.add(new QueryDBPedia(entity).getModel());
			
			
			System.out.println("Complete model size: " + model.size());
			//System.out.println(model);
			//Update list of cached entities 
			inCache.add(entity.getType() + "_" + entity.getName());
		}
		
	}

	private HashMap<String,HashMap<String, Integer>> executeLocalQuery(String query, Hashtable<String, String> propDic){
		//Result structure (PropertyKey,(Value,Count))
		HashMap<String,HashMap<String, Integer>> result = new HashMap<String,HashMap<String, Integer>>();
		
		//label is always present and has special logic -> enhance property dictionary just for reading them 
		Hashtable<String, String> enhDic = new Hashtable<String, String> ();
		enhDic.putAll(propDic);
		enhDic.put("label", "l");
		
		Query q = QueryFactory.create(query); 
		//System.out.println(q);
		InfModel imodel = ModelFactory.createRDFSModel(model);
		QueryExecution qe = QueryExecutionFactory.create(q, imodel);
		ResultSet RS = qe.execSelect();
		
		
		while (RS.hasNext()) {
			QuerySolution tuple = RS.next();
			handleQueryTuple(tuple, enhDic,result);
		}
		System.out.println("Result rows (local): " + RS.getRowNumber());
		qe.close();
		return result;	
	}
	
	private void handleQueryTuple(QuerySolution tuple,
			Hashtable<String, String> propDic, HashMap<String,HashMap<String, Integer>> result) {
		String v = "";
		String k = "";
		HashMap<String, Integer> tempMap = new HashMap<String, Integer>();
		
		//handle dynamic properties
		for (Entry<String,String> entry: propDic.entrySet()) {
			tempMap = new HashMap<String, Integer>();
			v = new String();
			k = new String(); 
			k = entry.getKey();
			if(tuple.contains(entry.getValue())){
				v = tuple.get(entry.getValue()).toString();
				if(!result.containsKey(k)){
					//key new -> add key, value with count 1
					tempMap.put(v, 1);
					result.put(k, tempMap);
				}
				else if(!result.get(k).containsKey(v)){
					//key existing, but new value -> add new value with count 1
					result.get(k).put(v, 1);					
				}else{
					//key and value existing -> increment counter					
					result.get(k).replace(v, result.get(k).get(v)+1);
				}
				
			}
		}
		
	}

	private Hashtable<String, String> prepareProperties(List<String> props) {
		Hashtable<String, String> properties = new Hashtable<String, String>();
		int i = 1;
		for (String prop : props) {
			properties.put(prop, Integer.toString(i));
			i++;
		}
		return properties;
	}

	private String constructLocalQuery(NamedEntity entity, Hashtable<String, String> props) {
		String queryString = "";
		String type = "";
		String name = "";
		
	
		// ---- Derive values ----
		// rdf:type 
		switch (entity.getType()) {
		case ORGANIZATION:
			type = "dbo:Organisation";
			break;
		case PERSON:
			type = "dbo:Person";
			break;
		case LOCATION:
			type = "dbo:Location";
			break;
		}
		// rdf:label Regex
		name = "(\\\\s+|^)" + entity.getName() + "((\\\\s+.*)|$)";
		
		
		// ---- Construct Query ----------
		// 1) Prefix
		queryString += "PREFIX owl: <http://www.w3.org/2002/07/owl#>"
				+ " PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>"
				+ " PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
				+ " PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ " PREFIX foaf: <http://xmlns.com/foaf/0.1/>"
				+ " PREFIX dc: <http://purl.org/dc/elements/1.1/>"
				+ " PREFIX dbo: <http://dbpedia.org/ontology/>"
				+ " PREFIX dbr: <http://dbpedia.org/resource/>"
				+ " PREFIX dbp: <http://dbpedia.org/property/>"
				+ " PREFIX dbpedia: <http://dbpedia.org/>"
				+ " PREFIX skos: <http://www.w3.org/2004/02/skos/core#>"				
			;
		// 2) Select Clause
		queryString += " SELECT ?l ";
		for (Entry<String,String> entry: props.entrySet()) {
			queryString += " ?" + entry.getValue();
		}	
		// 3) Where Clause	
		queryString += " WHERE {"
				+ "?e rdf:type " + type + ". "
				+ "?e rdfs:label ?l."
			;
		// 3b) dynamic part
		queryString += " OPTIONAL { ";
		for (Entry<String,String> entry: props.entrySet()) {
			queryString += "?e " + entry.getKey() + " ?" + entry.getValue() + ".";
		}
		queryString += " }";
		// 3c) Filter
		queryString += " FILTER(regex(?l,'" + name + "') && LANGMATCHES(LANG(?l), 'en'))";
		queryString += "}"; 
		//System.out.println(queryString);
		
		return queryString;
	}

	
	@SuppressWarnings("serial")
	private void initAvailableProperties(){
		this.qp = new QueryProperties();
		//Test:
		List<String> props = new ArrayList<String>() {{
		    add("dbp:homepage");
		    add("rdf:type");
		}};				
		this.qp.put(EntityType.LOCATION, props);
		this.qp.put(EntityType.ORGANIZATION, props);
		this.qp.put(EntityType.PERSON, props);
		//TODO Jena?
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		//TEST
		NamedEntity ne = new NamedEntity("SAP SE", EntityType.ORGANIZATION);
		List<NamedEntity> list = new ArrayList<NamedEntity>();
		list.add(ne);
		JenaEngine e = new JenaEngine();
		e.queryEntityProperties(list);
	}
}
