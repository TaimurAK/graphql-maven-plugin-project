/** This template is custom **/
package ${pluginConfiguration.packageName};

/**
 * @author generated by graphql-java-generator
 * @see <a href="https://github.com/graphql-java-generator/graphql-java-generator">https://github.com/graphql-java-generator/graphql-java-generator</a>
 */
public enum ${object.javaName} {
	#foreach ($value in $object.values)${value.name}#if($foreach.hasNext), #end#end;
}
