/**
 * 
 */
package org.graphql.maven.plugin.samples.server.jpa;

import java.util.UUID;

import org.graphql.maven.plugin.samples.server.Human;
import org.springframework.data.repository.CrudRepository;

/**
 * @author EtienneSF
 */
public interface HumanRepository extends CrudRepository<Human, UUID> {

}