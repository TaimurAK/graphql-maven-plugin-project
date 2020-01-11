/**
 * 
 */
package com.graphql_java_generator.plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.persistence.Entity;

import org.springframework.stereotype.Component;

import com.graphql_java_generator.annotation.GraphQLNonScalar;
import com.graphql_java_generator.annotation.GraphQLScalar;
import com.graphql_java_generator.plugin.language.BatchLoader;
import com.graphql_java_generator.plugin.language.DataFetcher;
import com.graphql_java_generator.plugin.language.DataFetchersDelegate;
import com.graphql_java_generator.plugin.language.Field;
import com.graphql_java_generator.plugin.language.Relation;
import com.graphql_java_generator.plugin.language.RelationType;
import com.graphql_java_generator.plugin.language.Type;
import com.graphql_java_generator.plugin.language.Type.GraphQlType;
import com.graphql_java_generator.plugin.language.impl.AbstractType;
import com.graphql_java_generator.plugin.language.impl.BatchLoaderImpl;
import com.graphql_java_generator.plugin.language.impl.DataFetcherImpl;
import com.graphql_java_generator.plugin.language.impl.DataFetchersDelegateImpl;
import com.graphql_java_generator.plugin.language.impl.EnumType;
import com.graphql_java_generator.plugin.language.impl.FieldImpl;
import com.graphql_java_generator.plugin.language.impl.InterfaceType;
import com.graphql_java_generator.plugin.language.impl.ObjectType;
import com.graphql_java_generator.plugin.language.impl.RelationImpl;
import com.graphql_java_generator.plugin.language.impl.ScalarType;
import com.graphql_java_generator.plugin.schema_personalization.JsonSchemaPersonalization;

import graphql.language.AbstractNode;
import graphql.language.Definition;
import graphql.language.Document;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValue;
import graphql.language.EnumValueDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ListType;
import graphql.language.Node;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.SchemaDefinition;
import graphql.language.StringValue;
import graphql.language.TypeName;
import graphql.parser.Parser;
import lombok.Getter;

/**
 * This class generates the Java classes, from the documents. These documents are read from the
 * graphql-spring-boot-starter code, in injected here thanks to spring's magic.<BR/>
 * There is no validity check: we trust the information in the Document, as it is read by the GraphQL {@link Parser}.
 * <BR/>
 * The graphQL-java library maps both FieldDefinition and InputValueDefinition in very similar structures, which are
 * actually trees. These structures are too hard too read in a Velocity template, and we need to parse down to a
 * properly structures way for that.
 * 
 * @author EtienneSF
 */
@Component
@Getter
public class DocumentParser {

	final String DEFAULT_QUERY_NAME = "Query";
	final String DEFAULT_MUTATION_NAME = "Mutation";
	final String DEFAULT_SUBSCRIPTION_NAME = "Subscription";

	/**
	 * This instance is responsible for providing all the configuration parameter from the project (Maven, Gradle...)
	 */
	@Resource
	PluginConfiguration pluginConfiguration;

	/////////////////////////////////////////////////////////////////////////////////////////////
	// Internal attributes for this class

	@Resource
	List<Document> documents;

	/**
	 * The {@link JsonSchemaPersonalization} allows the user to update what the plugin would have generate, through a
	 * json configuration file
	 */
	@Resource
	JsonSchemaPersonalization jsonSchemaPersonalization;

	/**
	 * All the Query Types for this Document. There may be several ones, if more than one GraphQLs files have been
	 * merged
	 */
	@Getter
	List<ObjectType> queryTypes = new ArrayList<>();

	/**
	 * All the Subscription Types for this Document. There may be several ones, if more than one GraphQLs files have
	 * been merged
	 */
	@Getter
	List<ObjectType> subscriptionTypes = new ArrayList<>();

	/**
	 * All the Mutation Types for this Document. There may be several ones, if more than one GraphQLs files have been
	 * merged
	 */
	@Getter
	List<ObjectType> mutationTypes = new ArrayList<>();

	/**
	 * All the {@link ObjectType} which have been read during the reading of the documents
	 */
	@Getter
	List<ObjectType> objectTypes = new ArrayList<>();

	/**
	 * All the {@link InterfaceTypeDefinition} which have been read during the reading of the documents
	 */
	@Getter
	List<InterfaceType> interfaceTypes = new ArrayList<>();

	/**
	 * All the {@link ObjectType} which have been read during the reading of the documents
	 */
	@Getter
	List<EnumType> enumTypes = new ArrayList<>();

	/** All the {@link Type}s that have been parsed, added by the default scalars */
	Map<String, com.graphql_java_generator.plugin.language.Type> types = new HashMap<>();

	/** All {@link Relation}s that have been found in the GraphQL schema(s) */
	List<Relation> relations = new ArrayList<>();

	/**
	 * All {@link DataFetcher}s that need to be implemented for this/these schema/schemas
	 */
	List<DataFetcher> dataFetchers = new ArrayList<>();

	/**
	 * All {@link DataFetchersDelegate}s that need to be implemented for this/these schema/schemas
	 */
	List<DataFetchersDelegate> dataFetchersDelegates = new ArrayList<>();

	/**
	 * All {@link BatchLoader}s that need to be implemented for this/these schema/schemas
	 */
	List<BatchLoader> batchLoaders = new ArrayList<>();

	/**
	 * maps for all scalers, when they are mandatory. The key is the type name. The value is the class to use in the
	 * java code
	 */
	List<ScalarType> scalars = new ArrayList<>();

	@PostConstruct
	public void postConstruct() {
		// Add of all predefined scalars
		scalars.add(new ScalarType("ID", "java.lang", "String", pluginConfiguration.getMode()));
		scalars.add(new ScalarType("UUID", "java.util", "UUID", pluginConfiguration.getMode()));
		scalars.add(new ScalarType("String", "java.lang", "String", pluginConfiguration.getMode()));

		// It seems that both boolean&Boolean, int&Int, float&Float are accepted.
		scalars.add(new ScalarType("boolean", "java.lang", "Boolean", pluginConfiguration.getMode()));
		scalars.add(new ScalarType("Boolean", "java.lang", "Boolean", pluginConfiguration.getMode()));
		scalars.add(new ScalarType("int", "java.lang", "Integer", pluginConfiguration.getMode()));
		scalars.add(new ScalarType("Int", "java.lang", "Integer", pluginConfiguration.getMode()));
		scalars.add(new ScalarType("Float", "java.lang", "Float", pluginConfiguration.getMode()));
		scalars.add(new ScalarType("float", "java.lang", "Float", pluginConfiguration.getMode()));
	}

	/**
	 * The main method of the class: it executes the generation of the given documents
	 * 
	 * @param documents
	 *            The GraphQL definition schema, from which the code is to be generated
	 * @return
	 */
	public int parseDocuments() {
		int nbClasses = documents.stream().mapToInt(this::parseOneDocument).sum();

		// Let's finalize some "details":

		// Each interface should have an implementation class, for JSON deserialization,
		// or to map to a JPA Entity
		nbClasses += defineDefaultInterfaceImplementationClassName();
		// init the list of the object implementing each interface.
		initListOfImplementations();
		// The types Map allows to retrieve easily a Type from its name
		fillTypesMap();
		// Let's identify every relation between objects, interface or union in the
		// model
		initRelations();
		// Some annotations are needed for Jackson or JPA
		addAnnotations();
		// List all data fetchers
		initDataFetchers();
		// List all Batch Loaders
		initBatchLoaders();

		// Apply the user's schema personalization
		jsonSchemaPersonalization.applySchemaPersonalization();

		return nbClasses;
	}

	/**
	 * Generates the target classes for the given GraphQL schema definition
	 * 
	 * @param document
	 */
	int parseOneDocument(Document document) {
		// List of all the names of the query types. There should be only one. But we're ready for more (for instance if
		// several schema files have been merged)
		List<String> queryObjectNames = new ArrayList<>();
		// List of all the names of the mutation types. There should be only one. But we're ready for more (for instance
		// if several schema files have been merged)
		List<String> mutationObjectNames = new ArrayList<>();
		// List of all the names of the subscription types. There should be only one. But we're ready for more (for
		// instance if several schema files have been merged)
		List<String> subscriptionObjectNames = new ArrayList<>();

		// Looks for a schema definitions, to list the defined queries, mutations and subscriptions (should be only one
		// of each), but we're ready for more. (for instance if several schema files have been merged)
		for (Definition<?> node : document.getDefinitions()) {
			if (node instanceof SchemaDefinition) {
				readSchemaDefinition((SchemaDefinition) node, queryObjectNames, mutationObjectNames,
						subscriptionObjectNames);
			} // if
		} // for

		for (Definition<?> node : document.getDefinitions()) {
			if (node instanceof ObjectTypeDefinition) {
				// Let's check what kind of ObjectDefinition we have
				String name = ((ObjectTypeDefinition) node).getName();
				if (queryObjectNames.contains(name) || DEFAULT_QUERY_NAME.equals(name)) {
					ObjectType query = readObjectType((ObjectTypeDefinition) node);
					query.setRequestType("query");
					queryTypes.add(query);
				} else if (mutationObjectNames.contains(name) || DEFAULT_MUTATION_NAME.equals(name)) {
					ObjectType mutation = readObjectType((ObjectTypeDefinition) node);
					mutation.setRequestType("mutation");
					mutationTypes.add(mutation);
				} else if (subscriptionObjectNames.contains(name) || DEFAULT_SUBSCRIPTION_NAME.equals(name)) {
					ObjectType subscription = readObjectType((ObjectTypeDefinition) node);
					subscription.setRequestType("subscription");
					subscriptionTypes.add(subscription);
				} else {
					objectTypes.add(readObjectType((ObjectTypeDefinition) node));
				}
			} else if (node instanceof InputObjectTypeDefinition) {
				objectTypes.add(readInputObjectType((InputObjectTypeDefinition) node));
			} else if (node instanceof EnumTypeDefinition) {
				enumTypes.add(readEnumType((EnumTypeDefinition) node));
			} else if (node instanceof InterfaceTypeDefinition) {
				interfaceTypes.add(readInterfaceType((InterfaceTypeDefinition) node));
			} else if (node instanceof SchemaDefinition) {
				// No action, we already parsed it
			} else {
				throw new RuntimeException("Unknown node type: " + node.getClass().getName());
			}
		} // for

		return queryTypes.size() + subscriptionTypes.size() + mutationTypes.size() + objectTypes.size()
				+ enumTypes.size() + interfaceTypes.size();
	}

	/**
	 * Fill the {@link #types} map, from all the types (object, interface, enum, scalars) that are valid for this
	 * schema. This allow to get the properties from their type, as only their type's name is known when parsing the
	 * schema.
	 */
	void fillTypesMap() {
		scalars.stream().forEach(s -> types.put(s.getName(), s));
		objectTypes.stream().forEach(o -> types.put(o.getName(), o));
		interfaceTypes.stream().forEach(i -> types.put(i.getName(), i));
		enumTypes.stream().forEach(e -> types.put(e.getName(), e));
	}

	/**
	 * @param schemaDef
	 * @param queryObjectNames
	 * @param mutationObjectNames
	 * @param subscriptionObjectNames
	 * 
	 */
	void readSchemaDefinition(SchemaDefinition schemaDef, List<String> queryObjectNames,
			List<String> mutationObjectNames, List<String> subscriptionObjectNames) {

		for (OperationTypeDefinition opDef : schemaDef.getOperationTypeDefinitions()) {
			TypeName type = opDef.getTypeName();
			switch (opDef.getName()) {
			case "query":
				queryObjectNames.add(type.getName());
				break;
			case "mutation":
				mutationObjectNames.add(type.getName());
				break;
			case "subscription":
				subscriptionObjectNames.add(type.getName());
				break;
			default:
				throw new RuntimeException(
						"Unexpected OperationTypeDefinition while reading schema: " + opDef.getName());
			}// switch
		} // for
	}

	/**
	 * Read an object type from it GraphQL definition
	 * 
	 * @param node
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	ObjectType readObjectType(ObjectTypeDefinition node) {
		// Let's check if it's a real object, or part of a schema (query, subscription,
		// mutation) definition

		ObjectType objectType = new ObjectType(pluginConfiguration.getPackageName(), pluginConfiguration.getMode());

		objectType.setName(node.getName());

		// Let's read all its fields
		objectType.setFields(node.getFieldDefinitions().stream().map(def -> readField(def, objectType))
				.collect(Collectors.toList()));

		// Let's read all the other object types that this one implements
		for (graphql.language.Type type : node.getImplements()) {
			if (type instanceof TypeName) {
				objectType.getImplementz().add(((TypeName) type).getName());
			} else if (type instanceof EnumValue) {
				objectType.getImplementz().add(((EnumValue) type).getName());
			} else {
				throw new RuntimeException("Non managed object type '" + type.getClass().getName()
						+ "' when listing implementations for the object '" + node.getName() + "'");
			}
		} // for

		return objectType;
	}

	/**
	 * Read an input object type from it GraphQL definition
	 * 
	 * @param node
	 * @return
	 */
	ObjectType readInputObjectType(InputObjectTypeDefinition node) {

		ObjectType objectType = new ObjectType(pluginConfiguration.getPackageName(), pluginConfiguration.getMode());
		objectType.setInputType(true);

		objectType.setName(node.getName());

		// Let's read all its fields
		for (InputValueDefinition def : node.getInputValueDefinitions()) {
			FieldImpl field = readFieldTypeDefinition(def);
			field.setOwningType(objectType);

			objectType.getFields().add(field);
		}

		return objectType;
	}

	/**
	 * Read an object type from it GraphQL definition
	 * 
	 * @param node
	 * @return
	 */
	InterfaceType readInterfaceType(InterfaceTypeDefinition node) {
		// Let's check if it's a real object, or part of a schema (query, subscription,
		// mutation) definition

		InterfaceType interfaceType = new InterfaceType(pluginConfiguration.getPackageName(),
				pluginConfiguration.getMode());

		interfaceType.setName(node.getName());

		// Let's read all its fields
		interfaceType.setFields(node.getFieldDefinitions().stream().map(def -> readField(def, interfaceType))
				.collect(Collectors.toList()));

		return interfaceType;
	}

	/**
	 * Reads an enum definition, and create the relevant {@link EnumType}
	 * 
	 * @param node
	 * @return
	 */
	EnumType readEnumType(EnumTypeDefinition node) {
		EnumType enumType = new EnumType(pluginConfiguration.getPackageName(), pluginConfiguration.getMode());
		enumType.setName(node.getName());
		for (EnumValueDefinition enumValDef : node.getEnumValueDefinitions()) {
			enumType.getValues().add(enumValDef.getName());
		} // for
		return enumType;
	}

	/**
	 * Reads one GraphQL {@link FieldDefinition}, and maps it into a {@link Field}.
	 * 
	 * @param fieldDef
	 * @param owningType
	 *            The type which contains this field
	 * @return
	 * @throws MojoExecutionException
	 */
	Field readField(FieldDefinition fieldDef, Type owningType) {

		FieldImpl field = readFieldTypeDefinition(fieldDef);
		field.setOwningType(owningType);

		// Let's read all its input parameters
		field.setInputParameters(fieldDef.getInputValueDefinitions().stream().map(this::readFieldTypeDefinition)
				.collect(Collectors.toList()));

		return field;
	}

	/**
	 * Reads a field, which can be either a GraphQL {@link FieldDefinition} or an {@link InputValueDefinition}, and maps
	 * it into a {@link Field}. The graphQL-java library maps both FieldDefinition and InputValueDefinition in very
	 * similar structures, which are actually trees. These structures are too hard too read in a Velocity template, and
	 * we need to parse down to a properly structures way for that.
	 * 
	 * @param fieldDef
	 * @param field
	 * @return
	 */
	FieldImpl readFieldTypeDefinition(AbstractNode<?> fieldDef) {
		FieldImpl field = FieldImpl.builder().documentParser(this).build();

		field.setName((String) exec("getName", fieldDef));

		// Let's default value to false
		field.setMandatory(false);
		field.setList(false);
		field.setItemMandatory(false);

		TypeName typeName = null;
		if (exec("getType", fieldDef) instanceof TypeName) {
			typeName = (TypeName) exec("getType", fieldDef);
		} else if (exec("getType", fieldDef) instanceof NonNullType) {
			field.setMandatory(true);
			Node<?> node = ((NonNullType) exec("getType", fieldDef)).getType();
			if (node instanceof TypeName) {
				typeName = (TypeName) node;
			} else if (node instanceof ListType) {
				Node<?> subNode = ((ListType) node).getType();
				field.setList(true);
				if (subNode instanceof TypeName) {
					typeName = (TypeName) subNode;
				} else if (subNode instanceof NonNullType) {
					typeName = (TypeName) ((NonNullType) subNode).getType();
					field.setItemMandatory(true);
				} else {
					throw new RuntimeException("Case not found (subnode of a ListType). The node is of type "
							+ subNode.getClass().getName() + " (for field " + field.getName() + ")");
				}
			} else {
				throw new RuntimeException("Case not found (subnode of a NonNullType). The node is of type "
						+ node.getClass().getName() + " (for field " + field.getName() + ")");
			}
		} else if (exec("getType", fieldDef) instanceof ListType) {
			field.setList(true);
			Node<?> node = ((ListType) exec("getType", fieldDef)).getType();
			if (node instanceof TypeName) {
				typeName = (TypeName) node;
			} else if (node instanceof NonNullType) {
				typeName = (TypeName) ((NonNullType) node).getType();
				field.setItemMandatory(true);
			} else {
				throw new RuntimeException("Case not found (subnode of a ListType). The node is of type "
						+ node.getClass().getName() + " (for field " + field.getName() + ")");
			}
		}

		// We have the type. But we may not have parsed it yet. So we just write its
		// name. And will get the
		// com.graphql_java_generator.plugin.language.Type when generating the code.
		field.setTypeName(typeName.getName());
		if (typeName.getName().equals("ID")) {
			field.setId(true);

			// In server mode, we use the UUID type.
			// For client, we keep complying to the GraphQL schema
			if (pluginConfiguration.getMode().equals(PluginMode.server)) {
				field.setTypeName("UUID");
			}
		}

		// For InputValueDefinition, we may have a defaut value
		if (fieldDef instanceof InputValueDefinition) {
			Object defaultValue = ((InputValueDefinition) fieldDef).getDefaultValue();
			if (defaultValue != null) {
				if (defaultValue instanceof StringValue) {
					field.setDefaultValue(((StringValue) defaultValue).getValue());
				} else if (defaultValue instanceof EnumValue) {
					field.setDefaultValue(((EnumValue) defaultValue).getName());
				} else {
					throw new RuntimeException("DefaultValue of type " + defaultValue.getClass().getName()
							+ " is not managed (for field " + field.getName() + ")");
				}
			}
		}

		return field;
	}

	/**
	 * Calls the 'methodName' method on the given object
	 * 
	 * @param methodName
	 *            The name of the method name
	 * @param object
	 *            The given node, on which the 'methodName' method is to be called
	 * @return
	 */
	Object exec(String methodName, Object object) {
		try {
			Method getType = object.getClass().getDeclaredMethod(methodName);
			return getType.invoke(object);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
				| SecurityException e) {
			throw new RuntimeException("Error when trying to execute '" + methodName + "' on '"
					+ object.getClass().getName() + "': " + e.getMessage(), e);
		}
	}

	/**
	 * A utility method, which maps an object type to the class full name of the Java class which will be generated for
	 * this object type. This utility method is based on the {@link PluginConfiguration#getPackageName()} plugin
	 * attribute, available in this class
	 * 
	 * @param name
	 */
	String getGeneratedFieldFullClassName(String name) {
		return pluginConfiguration.getPackageName() + "." + name;
	}

	/**
	 * This method add an {@link ObjectType} for each GraphQL interface, to the list of objects to create. The name of
	 * the object is typically the name of the interface, suffixed by "Impl". A test is done to insure that there is no
	 * "name collision", that is: that InterfaceNameImpl doesn't exist. If there is a collision, the method attempts to
	 * suffix Impl1, then Impl2... until there is no collision.<BR/>
	 * Note: this is useful only for the client code generation (not for the server one)
	 */
	int defineDefaultInterfaceImplementationClassName() {
		String objectName = "interface name to define";
		int nbGeneratedClasses = 0;

		for (InterfaceType i : interfaceTypes) {
			String defaultName = i.getName() + "Impl";
			boolean nameFound = true;
			int objectNamePrefix = 0;

			while (nameFound) {
				objectName = defaultName + (objectNamePrefix == 0 ? "" : objectNamePrefix);
				objectNamePrefix += 1;
				nameFound = false;
				for (Type o : objectTypes) {
					if (o.getName().equals(objectName)) {
						nameFound = true;
						break;
					}
				} // for (ObjectType)
			} // while

			// We've found a non used name for the interface implementation.
			ObjectType o = new ObjectType(pluginConfiguration.getPackageName(), pluginConfiguration.getMode());
			o.setName(objectName);
			List<String> interfaces = new ArrayList<>();
			interfaces.add(i.getName());
			o.setImplementz(interfaces);
			// We need to properly clone the fields, to attach them to the correct owning class
			for (Field sourceField : i.getFields()) {
				// We use the Lombok toBuilder facility to clone the source field and only change the owning type.
				Field targetField = ((FieldImpl) sourceField).toBuilder().owningType(o).build();
				// Then, we attach the 'new' field to the new object
				o.getFields().add(targetField);
			}
			o.setDefaultImplementationForInterface(i);
			objectTypes.add(o);
			nbGeneratedClasses += 1;

			i.setDefaultImplementation(o);

		} // for

		return nbGeneratedClasses;
	}

	/**
	 * Returns the type for the given name
	 * 
	 * @param typeName
	 * @return
	 */
	public com.graphql_java_generator.plugin.language.Type getType(String typeName) {
		return types.get(typeName);
	}

	/**
	 * Returns the {@link DataFetchersDelegate} that manages the given type.
	 * 
	 * @param type
	 *            The type, for which the DataFetchersDelegate is searched. It may not be null.
	 * @param createIfNotExists
	 *            if true: a new DataFetchersDelegate is created when there is no {@link DataFetchersDelegate} for this
	 *            type yet. If false: no DataFetchersDelegate creation.
	 * @return The relevant DataFetchersDelegate, or null of there is no DataFetchersDelegate for this type and
	 *         createIfNotExists is false
	 * @throws NullPointerException
	 *             If type is null
	 */
	public DataFetchersDelegate getDataFetchersDelegate(Type type, boolean createIfNotExists) {
		if (type == null) {
			throw new NullPointerException("type may not be null");
		}

		for (DataFetchersDelegate dfd : dataFetchersDelegates) {
			if (dfd.getType().equals(type)) {
				return dfd;
			}
		}

		// No DataFetchersDelegate for this type exists yet
		if (createIfNotExists) {
			DataFetchersDelegate dfd = new DataFetchersDelegateImpl(type);
			dataFetchersDelegates.add(dfd);
			return dfd;
		} else {
			return null;
		}
	}

	/**
	 * Reads all the GraphQl objects, interfaces, union... that have been read from the GraphQL schema, and list all the
	 * relations between Server objects (that is: all objects out of the Query/Mutation/Subscription types and the input
	 * types). The found relations are stored, to be reused during the code generation.<BR/>
	 * These relations are important for the server mode of the plugin, to generate the proper JPA annotations.
	 */
	void initRelations() {
		for (Type type : getObjectTypes()) {
			if (!type.isInputType()) {
				for (Field field : type.getFields()) {
					if (field.getType() instanceof ObjectType) {
						RelationType relType = field.isList() ? RelationType.OneToMany : RelationType.ManyToOne;
						RelationImpl relation = new RelationImpl(type, field, relType);
						//
						((FieldImpl) field).setRelation(relation);
						relations.add(relation);
					} // if (instanceof ObjectType)
				} // if (!type.isInputType())
			} // for (field)
		} // for (type)
	}

	/**
	 * Defines the annotation for each field of the read objects and interfaces. For the client mode, this is
	 * essentially the Jackson annotations, to allow deserialization of the server response, into the generated classes.
	 * For the server mode, this is essentially the JPA annotations, to define the interaction with the database,
	 * through Spring Data
	 */
	void addAnnotations() {
		// No annotation for types.
		// We go through each field of each type we generate, to define the relevant
		// annotation
		switch (pluginConfiguration.getMode()) {
		case client:
			Stream.concat(objectTypes.stream(), interfaceTypes.stream())
					.forEach(o -> addTypeAnnotationForClientMode(o));
			Stream.concat(objectTypes.stream(), interfaceTypes.stream()).flatMap(o -> o.getFields().stream())
					.forEach(f -> addFieldAnnotationForClientMode(f));
			break;
		case server:
			Stream.concat(objectTypes.stream(), interfaceTypes.stream())
					.forEach(o -> addTypeAnnotationForServerMode(o));
			Stream.concat(objectTypes.stream(), interfaceTypes.stream()).flatMap(o -> o.getFields().stream())
					.forEach(f -> addFieldAnnotationForServerMode(f));
			break;
		}

	}

	/**
	 * This method add the needed annotation(s) to the given type, when in client mode
	 * 
	 * @param o
	 */
	void addTypeAnnotationForClientMode(Type o) {
		// No specific annotation for objects and interfaces when in client mode.

		if (o.getName().startsWith("Character")) {
			int breakpoint = 1;
			System.out.print(breakpoint);
		}

		// Let's add the annotations, that are common to both the client and the server mode
		addTypeAnnotationForBothClientAndServerMode(o);
	}

	/**
	 * This method add the needed annotation(s) to the given type when in server mode. This typically add the
	 * JPA @{@link Entity} annotation.
	 * 
	 * @param o
	 */
	void addTypeAnnotationForServerMode(Type o) {

		if (!o.isInputType()) {
			if (o instanceof ObjectType && !(o instanceof InterfaceType)) {
				((AbstractType) o).addAnnotation("@Entity");
			}
		}

		// Let's add the annotations, that are common to both the client and the server mode
		addTypeAnnotationForBothClientAndServerMode(o);
	}

	/**
	 * This method add the needed annotation(s) to the given type when in server mode. This typically add
	 * the @{@link GraphQLInputType} annotation.
	 * 
	 * @param o
	 */
	private void addTypeAnnotationForBothClientAndServerMode(Type o) {
		if (o.isInputType()) {
			((AbstractType) o).addAnnotation("@GraphQLInputType");
		}
	}

	/**
	 * This method add the needed annotation(s) to the given field. It should be called when the maven plugin is in
	 * client mode. This typically add the Jackson annotation, to allow the desialization of the GraphQL server
	 * response.
	 * 
	 * @param field
	 */
	void addFieldAnnotationForClientMode(Field field) {
		if (field.getOwningType().getName().equals("CharacterImpl") && field.getName().equals("id")) {
			int breakpoint = 1;
			System.out.print(breakpoint);
		}
		if (field.getOwningType().getName().equals("Character") && field.getName().equals("id")) {
			int breakpoint = 1;
			System.out.print(breakpoint);
		}
		if (field.isList()) {
			((FieldImpl) field).addAnnotation(
					"@JsonDeserialize(contentAs = " + field.getType().getConcreteClassSimpleName() + ".class)");
		}

		addFieldAnnotationForBothClientAndServerMode(field);
	}

	/**
	 * This method add the needed annotation(s) to the given field. It should be called when the maven plugin is in
	 * server mode. This typically add the JPA @Id, @GeneratedValue, @Transient annotations.
	 * 
	 * @param field
	 */
	void addFieldAnnotationForServerMode(Field field) {
		if (!field.getOwningType().isInputType()) {
			if (field.isId()) {
				// We have found the identifier
				((FieldImpl) field).addAnnotation("@Id");
				((FieldImpl) field).addAnnotation("@GeneratedValue");
			} else if (field.getRelation() != null || field.isList()) {
				// We prevent JPA to manage the relations: we want the GraphQL Data Fetchers to do it, instead.
				((FieldImpl) field).addAnnotation("@Transient");
			}
		}

		addFieldAnnotationForBothClientAndServerMode(field);
	}

	/**
	 * This method add the annotation(s) that are common to the server and the client mode, to the given field. It
	 * typically adds the {@link GraphQLScalar} and {@link GraphQLNonScalar} annotations, to allow runtime management of
	 * the generated code.
	 * 
	 * @param field
	 */
	void addFieldAnnotationForBothClientAndServerMode(Field field) {
		if (field.getType() instanceof ScalarType || field.getType() instanceof EnumType) {
			((FieldImpl) field)
					.addAnnotation("@GraphQLScalar(graphqlType = " + field.getType().getClassSimpleName() + ".class)");
		} else {
			((FieldImpl) field).addAnnotation(
					"@GraphQLNonScalar(graphqlType = " + field.getType().getClassSimpleName() + ".class)");
		}
	}

	/**
	 * Identified all the GraphQL Data Fetchers needed from this/these schema/schemas
	 */
	void initDataFetchers() {
		if (pluginConfiguration.getMode().equals(PluginMode.server)) {
			queryTypes.stream().forEach(o -> initDataFetcherForOneObject(o, true));
			mutationTypes.stream().forEach(o -> initDataFetcherForOneObject(o, true));
			objectTypes.stream().forEach(o -> initDataFetcherForOneObject(o, false));
			interfaceTypes.stream().forEach(o -> initDataFetcherForOneObject(o, false));
		}
	}

	/**
	 * Identified all the GraphQL Data Fetchers needed for this type
	 *
	 * @param type
	 * @param isQueryOrMutationType
	 *            true if the given type is actually a query, false otherwise
	 */
	void initDataFetcherForOneObject(ObjectType type, boolean isQueryOrMutationType) {

		// No DataFetcher for :
		// 1) the "artificial" Object Type created to instanciate an Interface. This "artificial" Object
		// Type is for internal usage only, and to be used in Client mode to allow instanciation of the server response
		// interface object. It doesn't exist in the GraphQL Schema. Thus, it must have no DataFetchersDelegate.
		// 2) the input type
		if (type.getDefaultImplementationForInterface() == null && !type.isInputType()) {

			// Creation of the DataFetchersDelegate. It will be added to the list only if it contains at least one
			// DataFetcher.
			DataFetchersDelegate dataFetcherDelegate = new DataFetchersDelegateImpl(type);

			for (Field field : type.getFields()) {
				DataFetcherImpl dataFetcher = null;

				if (isQueryOrMutationType) {
					// For queries and field that are lists, we take the argument read in the schema
					// as is: all the needed
					// informations is already parsed.
					dataFetcher = new DataFetcherImpl(field, false);
				} else if (((type instanceof ObjectType || type instanceof InterfaceType) && //
						(field.isList() || field.getType() instanceof ObjectType
								|| field.getType() instanceof InterfaceType))) {
					// For Objects and Interfaces, we need to add a specific data fetcher. The objective there is to
					// manage
					// the relations with GraphQL, and not via JPA. The aim is to use the GraphQL data loader : very
					// important to limit the number of subqueries, when subobjects are queried.
					// In these case, we need to create a new field that add the object ID as a parameter of the Data
					// Fetcher
					FieldImpl newField = FieldImpl.builder().documentParser(this).name(field.getName())
							.list(field.isList()).owningType(field.getOwningType()).typeName(field.getTypeName())
							.build();

					// Let's add the id for the owning type of the field, then all its input parameters
					for (Field inputParameter : field.getInputParameters()) {
						List<Field> list = newField.getInputParameters();
						list.add(inputParameter);
					}

					// We'll use a Batch Loader if:
					// 1) It's a Data Fetcher from an object to another one (we're already in this case)
					// 2) That target object has an id (it can be either a list or a single object)
					// 3) The Relation toward the target object is OneToOne or ManyToOne. That is this field is not a
					// list
					boolean useBatchLoader = (field.getType().getIdentifier() != null) && (!field.isList());

					dataFetcher = new DataFetcherImpl(newField, useBatchLoader);
					dataFetcher.setSourceName(type.getName());
				}

				// If we found a DataFether, let's register it.
				if (dataFetcher != null) {
					dataFetcher.setDataFetcherDelegate(dataFetcherDelegate);
					dataFetcherDelegate.getDataFetchers().add(dataFetcher);
					dataFetchers.add(dataFetcher);
				}
			} // for

			// If at least one DataFetcher has been created, we register this
			// DataFetchersDelegate
			if (dataFetcherDelegate.getDataFetchers().size() > 0) {
				dataFetchersDelegates.add(dataFetcherDelegate);
			}
		}
	}

	/**
	 * Identify each BatchLoader to generate, and attach its {@link DataFetcher} to its {@link DataFetchersDelegate}.
	 * The whole stuff is stored into {@link #batchLoaders}
	 */
	private void initBatchLoaders() {
		if (pluginConfiguration.getMode().equals(PluginMode.server)) {
			// objectTypes contains both the objects defined in the schema, and the concrete objects created to map the
			// interfaces, along with Enums...

			// We fetch only the objects, here. The interfaces are managed just after
			objectTypes.stream().filter(o -> (o.getGraphQlType() == GraphQlType.OBJECT && !o.isInputType()))
					.forEach(o -> initOneBatchLoader(o));

			// Let's go through all interfaces.
			interfaceTypes.stream().forEach(i -> initOneBatchLoader(i));
		}
	}

	/**
	 * Analyzes one object, and decides if there should be a {@link BatchLoader} for it
	 * 
	 * @param type
	 *            the Type that may need a BatchLoader
	 */
	private void initOneBatchLoader(ObjectType type) {
		// No BatchLoader for the "artificial" Object Type created to instanciate an Interface. This "artificial" Object
		// Type is for internal usage only, and to be used in Client mode to allow instanciation of the server response
		// interface object. It doesn't exist in the GraphQL Schema. Thus, it must have no BatchLoader.
		if (type.getDefaultImplementationForInterface() == null) {
			Field id = type.getIdentifier();
			if (id != null) {
				batchLoaders.add(new BatchLoaderImpl(type, getDataFetchersDelegate(type, true)));
			}
		}
	}

	/**
	 * For each interface, identify the list of object types which implements it.
	 * 
	 * @see InterfaceType#getImplementingTypes()
	 */
	void initListOfImplementations() {
		for (InterfaceType interfaceType : interfaceTypes) {
			for (ObjectType objectType : objectTypes) {
				if (objectType.getImplementz().contains(interfaceType.getName())) {
					// This object implements the current interface we're looping in.
					interfaceType.getImplementingTypes().add(objectType);
				}
			}
		}
	}

}