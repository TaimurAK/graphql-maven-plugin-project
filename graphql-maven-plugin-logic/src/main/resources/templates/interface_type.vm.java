package ${pluginConfiguration.packageName};

import java.util.List;

#if (${pluginConfiguration.mode} == "server")
import java.util.UUID;

#if (${pluginConfiguration.generateJPAAnnotation})
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Transient;
#end
#end

#if (${pluginConfiguration.mode} == "client")
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
#end

import com.graphql_java_generator.annotation.GraphQLInputParameters;
import com.graphql_java_generator.annotation.GraphQLNonScalar;
import com.graphql_java_generator.annotation.GraphQLScalar;

/**
 * @author generated by graphql-java-generator
 * @see <a href="https://github.com/graphql-java-generator/graphql-java-generator">https://github.com/graphql-java-generator/graphql-java-generator</a>
 */
public interface ${object.name} #if($object.implementz.size()>0)implements #foreach($impl in $object.implementz)$impl#if($foreach.hasNext), #end#end#end {
#foreach ($field in $object.fields)

#if (${field.inputParameters.size()} > 0)
	@GraphQLInputParameters(names = {#foreach ($inputParameter in $field.inputParameters)"${inputParameter.name}"#if($foreach.hasNext), #end#end}, types = {#foreach ($inputParameter in $field.inputParameters)"${inputParameter.type.name}"#if($foreach.hasNext), #end#end})
#end
	${field.annotation}
	public void set${field.pascalCaseName}(#if(${field.list})List<#end${field.type.classSimpleName}#if(${field.list})>#end ${field.name});

	${field.annotation}
	public #if(${field.list})List<#end${field.type.classSimpleName}#if(${field.list})>#end get${field.pascalCaseName}();
#end
}
