package QueryEngine;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.riot.system.PrefixMapFactory;
import org.apache.jena.shared.PrefixMapping;

import NEREngine.NamedEntity;

public class QueryDBPedia {
	private Model model;

	public Model getModel(){
		return model;
	}
	
	public QueryDBPedia(NamedEntity e) {
		querySource(e);
	}
	
	

	private void querySource(NamedEntity e) {
		// ---- Definitions ---
		String endpoint = "http://dbpedia.org/sparql";
		String queryString = "";
		String type = "";
		String name = "";
				
		// ---- Derive values ----
		// rdf:label Regex
		name = e.getName();
		
		
		// rdf:type 
		switch (e.getType()) {
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
		
		// ---- Construct DESCRIBE Query ----------
		//* 1) Prefix
		queryString += "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>"
				+ " PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>"
				+ " PREFIX dbo: <http://dbpedia.org/resource/>"
			;
		// 2) DESCRIBE Clause
		queryString += " DESCRIBE ?e";
		// 3) Where Clause	
		queryString += " WHERE { "
				+ "?e rdf:type " + type + ". "
				+ "?e rdfs:label ?l.";
		// 3c) Filter
		queryString += " FILTER(regex(?l,'" + name + "') && LANGMATCHES(LANG(?l), 'en'))"
				+ " }"
			; 
		/*
		// rdf:type 
		switch (e.getType()) {
		case ORGANIZATION:
			type = "<http://dbpedia.org/resource/Organisation>";
			break;
		case PERSON:
			type = "<http://dbpedia.org/resource/Person>";
			break;
		case LOCATION:
			type = "<http://dbpedia.org/resource/Location>";
			break;
		}		

		// 2) DESCRIBE Clause
		queryString += "DESCRIBE ?e";
		// 3) Where Clause	
		queryString += " WHERE { "
				+ "?e <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> " + type + ". "
				+ "?e <http://www.w3.org/2000/01/rdf-schema#label> ?l.";
		// 3c) Filter
		queryString += " FILTER(regex(?l,'" + name + "') && LANGMATCHES(LANG(?l), 'en'))"
				+ " }"
			; 
		*/
		
		//queryString = "SELECT ?p ?o WHERE { <http://dbpedia.org/resource/SAP_SE> ?p ?o }"; 
		queryString = "DESCRIBE <http://dbpedia.org/resource/SAP_SE>"; 
		System.out.println(queryString);
		
		
		PrefixMapping pm = PrefixMapping.Factory.create();
		pm.setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
		pm.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
		pm.setNsPrefix("dbo", "http://dbpedia.org/resource/");
		ModelFactory.setDefaultModelPrefixes(pm);
		
		// ---- Execute Query --------
		Query q = QueryFactory.create(queryString); 
		QueryExecution qe = QueryExecutionFactory.sparqlService(endpoint, q);
		/*ResultSet RS = qe.execSelect();
		if(RS.hasNext()){
			System.out.println(RS.nextSolution());
		};
		
		model = RS.getResourceModel();*/
		model = qe.execDescribe();
		System.out.println(model.getNsPrefixMap());
		qe.close() ;
		System.out.println(model.isEmpty());
		System.out.println(model.size());
		System.out.println(model.toString());
		;
		
	}

}
