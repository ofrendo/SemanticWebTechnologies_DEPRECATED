package QueryEngine;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QueryParseException;
import org.apache.jena.rdf.model.Model;

import NEREngine.NamedEntity;
import NEREngine.NamedEntity.EntityType;

public class QuerySource {
	public enum Source {
	    DBPedia
	}

	private Model model;
	private String type;
	private String endpoint;
	private Source source;

	public Model getModel(){
		return model;
	}

	public QuerySource(Source s, EntityType et, List<NamedEntity> entities){ 
		this.source = s;
		determineSourceParameters(s,et);
		querySource(entities);
	}
	
	private void determineSourceParameters(Source s, EntityType et) {
		// rdf:type and enpoint definition
		switch (s){
		case DBPedia:
			endpoint = "http://dbpedia.org/sparql";
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
			break;
		}
	}

	private void querySource(List<NamedEntity> entities) {
		Long start = System.nanoTime();
		
		// ---- Definitions ---
		String queryString = "";
			

		// 2) DESCRIBE Clause
		queryString += "DESCRIBE ?e";
		// 3) Where Clause	
		queryString += " WHERE { "
				+ "?e <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> " + type + ". "
				+ "?e <http://www.w3.org/2000/01/rdf-schema#label> ?l."
			//	+ "?e <http://dbpedia.org/ontology/abstract> ?a."
			//	+ "?e <http://www.w3.org/2000/01/rdf-schema#comment> ?c."
				;
		// 3c) Filter		
		queryString += " FILTER( LANGMATCHES(LANG(?l), 'en')"
			//	+ " && LANGMATCHES(LANG(?a), 'en')"
			//	+ " && LANGMATCHES(LANG(?c), 'en')"
				;
		// 3d) dynamic filter part
		String filter = "";
		for (NamedEntity e : entities) {
			if(filter != ""){
				filter = " || ";
			}
			filter += "regex(?l,'" + e.getRegexName() + "')";
		}				
		queryString += " && ( " + filter + " ) ) }"; 
	
		//System.out.println(queryString);
		
		// ---- Execute Query --------
		Query q = null;		
		try {
			q = QueryFactory.create(queryString);
		} catch (QueryParseException e) {
			System.out.println(source + " query generation failed for: " + entities + " - query string:");
			System.out.println(queryString);
			return;
		}
		
		QueryExecution qe = QueryExecutionFactory.sparqlService(endpoint, q);
		
		try {
			model = qe.execDescribe();
			System.out.println("Queried "+ source +" for: " + entities + ", size: " + model.size() + "; time: " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start) + "ms");
		} catch (Exception e2) {
			System.out.println("Query for "+ source +" failed: " + e2.getMessage());
			System.out.println(q);
		} finally {
			qe.close() ;
		}			
	}
}
