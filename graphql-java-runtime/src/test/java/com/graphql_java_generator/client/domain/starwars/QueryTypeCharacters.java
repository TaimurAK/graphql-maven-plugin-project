package com.graphql_java_generator.client.domain.starwars;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;


/**
 * @author generated by graphql-java-generator
 * @see <a href="https://github.com/graphql-java-generator/graphql-java-generator">https://github.com/graphql-java-generator/graphql-java-generator</a>
 */
public class QueryTypeCharacters {

	public static final String NAME = "characters";

	@JsonDeserialize(contentAs = Character.class)
	@JsonProperty("characters")
	List<Character> characters;

	public void setCharacters(List<Character> characters) {
		this.characters = characters;
	}

	public List<Character> getCharacters() {
		return characters;
	}
	
    public String toString() {
        return "QueryTypeCharacters {characters: " + characters + "}";
    }
}
