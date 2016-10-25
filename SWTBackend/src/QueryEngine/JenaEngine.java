/**
 * 
 */
package QueryEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

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
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see QueryEngine.QueryEngine#getAvailableProperties(NEREngine.NamedEntity.EntityType)
	 */
	@Override
	public List<String> getAvailableProperties(EntityType type) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see QueryEngine.QueryEngine#queryEntityProperties(java.util.List)
	 */
	@Override
	public void queryEntityProperties(List<NamedEntity> entities) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see QueryEngine.QueryEngine#queryEntityProperties(java.util.List, java.util.Properties)
	 */
	@Override
	public void queryEntityProperties(List<NamedEntity> entities,
			Properties props) {
		// TODO Auto-generated method stub

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
		Properties p = new Properties();
		
	}
	
	private QueryProperties determineAvailableProperties(){
		
		//TODO
		return null;
	};

}
