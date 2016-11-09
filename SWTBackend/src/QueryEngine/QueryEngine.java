package QueryEngine;

import java.util.List;
import java.util.Properties;

import NEREngine.NamedEntity;
import NEREngine.NamedEntity.EntityType;


public interface QueryEngine {
	public Properties getAvailableProperties();
	public List<String> getAvailableProperties(EntityType type);
	
	
	//TODO Define return type
	public List<NamedEntity> queryEntities(List<NamedEntity> entities);
	public List<NamedEntity> queryEntities(List<NamedEntity> entities, QueryProperties props);
}
