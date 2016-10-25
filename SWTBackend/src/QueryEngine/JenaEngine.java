/**
 * 
 */
package QueryEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
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
		qp = determineAvailableProperties();
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
	 */
	@Override
	public void queryEntityProperties(List<NamedEntity> entities) {
		queryEntityProperties(entities, qp);
	}

	/* (non-Javadoc)
	 * @see QueryEngine.QueryEngine#queryEntityProperties(java.util.List, java.util.Properties)
	 */
	@Override
	public void queryEntityProperties(List<NamedEntity> entities,
			QueryProperties props) {
		for (NamedEntity namedEntity : entities) {
			//TODO define return type
			queryEntity(namedEntity, props.get(namedEntity.getType()));
		}

	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Properties p = new Properties();
		List<String> l = new ArrayList<String>();
		l.add("1");
		l.add("2");
		
		p.put("a",l);
		p.put("b", "2");
		
		System.out.println(p.toString());

	}
	
	private void queryEntity(NamedEntity entity, List<String> props){
		//Jena 
		
	}
	
	private void queryEntityInDBPedia(NamedEntity entity, List<String> props){
		String endpoint = "http://dbpedia.org/sparql";
		String queryString = "SELECT ?x ..."; 
		Query q = QueryFactory.create(queryString); 
		QueryExecution qe = QueryExecutionFactory.sparqlService(endpoint, q);
		ResultSet RS = qe.execSelect();
	}
	
	
	private QueryProperties determineAvailableProperties(){
		
		//TODO Jena?
		return null;
	};

}
