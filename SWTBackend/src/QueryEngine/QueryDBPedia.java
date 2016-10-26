package QueryEngine;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.rdf.model.Model;
import NEREngine.NamedEntity;

public class QueryDBPedia {
	private Model model;

	public Model getModel(){
		return model;
	}
	
	public QueryDBPedia(NamedEntity e) {
		querySource(e);
		System.out.println(model.size());
		//System.out.println(model.getNsPrefixMap());	
	}
	
	

	private void querySource(NamedEntity e) {
		// ---- Definitions ---
		String endpoint = "http://dbpedia.org/sparql";
		String queryString = "";
		String type = "";
		String name = "";
				
		// ---- Derive values ----
		// rdf:label Regex
		name = "(^.{0,5}\\\\s+|^)" + e.getName() + "((\\\\s+.{0,5}$)|$)";

		// rdf:type 
		switch (e.getType()) {
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
		queryString += " FILTER(regex(?l,'" + name + "') && LANGMATCHES(LANG(?l), 'en'))"
				+ " }"
			; 
	
		//System.out.println(queryString);
		
		/*
		PrefixMapping pm = PrefixMapping.Factory.create();
		pm.setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
		pm.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");
		pm.setNsPrefix("dbo", "http://dbpedia.org/resource/");
		ModelFactory.setDefaultModelPrefixes(pm);
		*/
		
		// ---- Execute Query --------
		Query q = QueryFactory.create(queryString); 
		QueryExecution qe = QueryExecutionFactory.sparqlService(endpoint, q);
		
		try {
			model = qe.execDescribe();
		} catch (Exception e2) {
			System.out.println("Query for DBPedia failed: " + e2.getMessage());
			System.out.println(q);
		} finally {
			qe.close() ;
		}		
		
	}

}
