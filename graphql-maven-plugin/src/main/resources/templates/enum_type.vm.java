package ${package};

/**
 * @author generated by graphql-maven-plugin
 */
public enum ${object.name} {
	#foreach ($value in $object.values)${value}#if($foreach.hasNext), #end#end;
}