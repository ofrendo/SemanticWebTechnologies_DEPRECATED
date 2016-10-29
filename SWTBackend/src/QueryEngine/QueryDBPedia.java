package QueryEngine;

import java.util.List;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.rdf.model.Model;

import NEREngine.NamedEntity.EntityType;

public class QueryDBPedia {
	private Model model;

	public Model getModel(){
		return model;
	}

	public QueryDBPedia(EntityType et, List<String> entities, String filter) {
		querySource(et, entities, filter);
	}
	
	private void querySource(EntityType et,List<String> entities, String filter) {
		// ---- Definitions ---
		String endpoint = "http://dbpedia.org/sparql";
		String queryString = "";
		String type = "";

		// rdf:type 
		switch (et) {
		case ORGANIZATION:
			type = "<http://dbpedia.org/ontology/Organisation>";
			break;
		case PERSON:
			type = "<http://dbpedia.org/ontology/Person>";
			break;
		case LOCATION:
			type = "<http://dbpedia.org/ontology/Location>";
			break;
		}		

		// 2) DESCRIBE Clause
		queryString += "DESCRIBE ?e";
		// 3) Where Clause	
		queryString += " WHERE { "
				+ "?e <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> " + type + ". "
				+ "?e <http://www.w3.org/2000/01/rdf-schema#label> ?l.";
		// 3c) Filter		
		queryString += " FILTER( LANGMATCHES(LANG(?l), 'en') && ( " + filter + " ) ) }"; 
	
		//System.out.println(queryString);
		
		// ---- Execute Query --------
		Query q = null;		
		try {
			q = QueryFactory.create(queryString);
		} catch (QueryParseException e) {
			System.out.println("Query generation for DBPedia for  "+ et + ": " + entities + " failed, query string:");
			System.out.println(queryString);
			return;
		}
		
		QueryExecution qe = QueryExecutionFactory.sparqlService(endpoint, q);
		
		try {
			model = qe.execDescribe();
			System.out.println("Queried DBPedia for "+ et + ": " + entities + ", size: " + model.size());
		} catch (Exception e2) {
			System.out.println("Query for DBPedia failed: " + e2.getMessage());
			System.out.println(q);
		} finally {
			qe.close() ;
		}			
	}
}
