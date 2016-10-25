package QueryEngine;

import java.util.List;
import java.util.Properties;

import NEREngine.NamedEntity;
import NEREngine.NamedEntity.EntityType;


public interface QueryEngine {
	public Properties getAvailableProperties();
	public List<String> getAvailableProperties(EntityType type);
	
	//TODO Define return type
	public void queryEntityProperties(List<NamedEntity> entities);
	public void queryEntityProperties(List<NamedEntity> entities, Properties props);
}
