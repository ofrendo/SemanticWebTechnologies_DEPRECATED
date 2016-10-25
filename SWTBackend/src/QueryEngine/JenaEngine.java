/**
 * 
 */
package QueryEngine;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.FileManager;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import NEREngine.NamedEntity;
import NEREngine.NamedEntity.EntityType;


/**
 * @author Sascha Ulbrich
 *
 */
public class JenaEngine implements QueryEngine {
	private QueryProperties qp;
	
	public JenaEngine() {
		initAvailableProperties();
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
	
	private Hashtable<String, String> queryEntity(NamedEntity entity, List<String> props){
		
		//Query Sources to build model
		QueryDBPedia q1 = new QueryDBPedia(entity);
		
		//construct dictionary for properties
		Hashtable<String, String> propDic = prepareProperties(props);
		
		//Construct local query
		String lq = constructLocalQuery(entity, propDic);
		
		//Test
		//lq = "PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#> SELECT DISTINCT ?l WHERE { ?x rdfs:label ?l. ?x rdfs:label ?1. }";
		
		
		//Execute Query
		return executeLocalQuery(lq,q1.getModel(),propDic);		
	}
	
	private Hashtable<String, String> executeLocalQuery(String query, Model model,Hashtable<String, String> propDic){
		Hashtable<String, String> result = new Hashtable<String, String>();
		Query q = QueryFactory.create(query); 
		QueryExecution qe = QueryExecutionFactory.create(q, model);
		ResultSet RS = qe.execSelect();
		int j = 0;
		while (RS.hasNext()) {
			j++;
			QuerySolution tuple = RS.nextSolution();
			result.put("label", tuple.getLiteral("l").getString());
			for (Entry<String,String> entry: propDic.entrySet()) {
				if(tuple.getLiteral(entry.getValue()) != null){
					result.put(entry.getKey(), tuple.getLiteral(entry.getValue()).getString());
				}
			}
		}
		System.out.println(Integer.toString(j));
		qe.close();
		return result;	
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
		name = entity.getName();
		
		// ---- Construct Query ----------
		// 1) Prefix
		queryString += "PREFIX owl: <http://www.w3.org/2002/07/owl#>"
				+ " PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>"
				+ " PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
				+ " PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ " PREFIX foaf: <http://xmlns.com/foaf/0.1/>"
				+ " PREFIX dc: <http://purl.org/dc/elements/1.1/>"
				+ " PREFIX dbo: <http://dbpedia.org/resource/>"
				+ " PREFIX dbpedia2: <http://dbpedia.org/property/>"
				+ " PREFIX dbpedia: <http://dbpedia.org/>"
				+ " PREFIX skos: <http://www.w3.org/2004/02/skos/core#>"				
			;
		// 2) Select Clause
		queryString += " SELECT ";
		for (Entry<String,String> entry: props.entrySet()) {
			queryString += "?" + entry.getValue();
		}	
		// 3) Where Clause	
		queryString += " WHERE {"
				+ "?e rdf:type " + type + ". "
				+ "?e rdfs:label ?l."
			//	+ "OPTIONAL { ?e dbp:homepage ?homepage. }"
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
		System.out.println(queryString);
		
		return queryString;
	}

	
	private void initAvailableProperties(){
		this.qp = new QueryProperties();
		//Test:
		List<String> props = new ArrayList<String>() {{
		    add("dbpedia2:homepage");
		    //add("rdfs:label");
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
