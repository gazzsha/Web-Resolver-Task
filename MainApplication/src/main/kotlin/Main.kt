package ru

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.admin.SpringApplicationAdminJmxAutoConfiguration
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration
import org.springframework.boot.autoconfigure.couchbase.CouchbaseAutoConfiguration
import org.springframework.boot.autoconfigure.data.cassandra.CassandraDataAutoConfiguration
import org.springframework.boot.autoconfigure.data.cassandra.CassandraReactiveDataAutoConfiguration
import org.springframework.boot.autoconfigure.data.cassandra.CassandraReactiveRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.data.cassandra.CassandraRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.data.couchbase.CouchbaseDataAutoConfiguration
import org.springframework.boot.autoconfigure.data.couchbase.CouchbaseReactiveDataAutoConfiguration
import org.springframework.boot.autoconfigure.data.couchbase.CouchbaseReactiveRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.data.couchbase.CouchbaseRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration
import org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.data.jdbc.JdbcRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.data.ldap.LdapRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration
import org.springframework.boot.autoconfigure.data.mongo.MongoReactiveDataAutoConfiguration
import org.springframework.boot.autoconfigure.data.mongo.MongoReactiveRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jDataAutoConfiguration
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jReactiveDataAutoConfiguration
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jReactiveRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.data.neo4j.Neo4jRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.data.r2dbc.R2dbcDataAutoConfiguration
import org.springframework.boot.autoconfigure.data.r2dbc.R2dbcRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.data.rest.RepositoryRestMvcAutoConfiguration
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerAutoConfiguration
import org.springframework.boot.autoconfigure.groovy.template.GroovyTemplateAutoConfiguration
import org.springframework.boot.autoconfigure.h2.H2ConsoleAutoConfiguration
import org.springframework.boot.autoconfigure.hateoas.HypermediaAutoConfiguration
import org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration
import org.springframework.boot.autoconfigure.hazelcast.HazelcastJpaDependencyAutoConfiguration
import org.springframework.boot.autoconfigure.integration.IntegrationAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.JndiDataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.XADataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.jms.JmsAutoConfiguration
import org.springframework.boot.autoconfigure.jms.JndiConnectionFactoryAutoConfiguration
import org.springframework.boot.autoconfigure.jms.activemq.ActiveMQAutoConfiguration
import org.springframework.boot.autoconfigure.jms.artemis.ArtemisAutoConfiguration
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration
import org.springframework.boot.autoconfigure.jooq.JooqAutoConfiguration
import org.springframework.boot.autoconfigure.jsonb.JsonbAutoConfiguration
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
import org.springframework.boot.autoconfigure.ldap.LdapAutoConfiguration
import org.springframework.boot.autoconfigure.ldap.embedded.EmbeddedLdapAutoConfiguration
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration
import org.springframework.boot.autoconfigure.mail.MailSenderValidatorAutoConfiguration
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration
import org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration
import org.springframework.boot.autoconfigure.mustache.MustacheAutoConfiguration
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration
import org.springframework.boot.autoconfigure.r2dbc.R2dbcTransactionManagerAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketMessagingAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketRequesterAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketServerAutoConfiguration
import org.springframework.boot.autoconfigure.rsocket.RSocketStrategiesAutoConfiguration
import org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration
import org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration
import org.springframework.boot.autoconfigure.security.rsocket.RSocketSecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.saml2.Saml2RelyingPartyAutoConfiguration
import org.springframework.boot.autoconfigure.sendgrid.SendGridAutoConfiguration
import org.springframework.boot.autoconfigure.session.SessionAutoConfiguration
import org.springframework.boot.autoconfigure.thymeleaf.ThymeleafAutoConfiguration
import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration
import org.springframework.boot.autoconfigure.web.reactive.ReactiveWebServerFactoryAutoConfiguration
import org.springframework.boot.autoconfigure.web.reactive.WebFluxAutoConfiguration
import org.springframework.boot.autoconfigure.web.reactive.error.ErrorWebFluxAutoConfiguration
import org.springframework.boot.autoconfigure.websocket.reactive.WebSocketReactiveAutoConfiguration
import org.springframework.boot.autoconfigure.websocket.servlet.WebSocketMessagingAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
@SpringBootApplication(
    exclude = [
        KafkaAutoConfiguration::class,
        ActiveMQAutoConfiguration::class,
        ArtemisAutoConfiguration::class,
        BatchAutoConfiguration::class,
        CassandraDataAutoConfiguration::class,
        CassandraReactiveDataAutoConfiguration::class,
        CassandraReactiveRepositoriesAutoConfiguration::class,
        CassandraRepositoriesAutoConfiguration::class,
        CouchbaseAutoConfiguration::class,
        CouchbaseDataAutoConfiguration::class,
        CouchbaseReactiveDataAutoConfiguration::class,
        CouchbaseReactiveRepositoriesAutoConfiguration::class,
        CouchbaseRepositoriesAutoConfiguration::class,
        ElasticsearchDataAutoConfiguration::class,
        ElasticsearchRepositoriesAutoConfiguration::class,
        ElasticsearchRestClientAutoConfiguration::class,
        EmbeddedLdapAutoConfiguration::class,
        ErrorWebFluxAutoConfiguration::class,
        FlywayAutoConfiguration::class,
        FreeMarkerAutoConfiguration::class,
        GroovyTemplateAutoConfiguration::class,
        H2ConsoleAutoConfiguration::class,
        HazelcastAutoConfiguration::class,
        HazelcastJpaDependencyAutoConfiguration::class,
        HttpHandlerAutoConfiguration::class,
        HypermediaAutoConfiguration::class,
        IntegrationAutoConfiguration::class,
        HypermediaAutoConfiguration::class,
        JdbcRepositoriesAutoConfiguration::class,
        JmsAutoConfiguration::class,
        JmxAutoConfiguration::class,
        JndiConnectionFactoryAutoConfiguration::class,
        JndiDataSourceAutoConfiguration::class,
        JooqAutoConfiguration::class,
        JsonbAutoConfiguration::class,
        LdapAutoConfiguration::class,
        LdapRepositoriesAutoConfiguration::class,
        MailSenderAutoConfiguration::class,
        MailSenderValidatorAutoConfiguration::class,
        MessageSourceAutoConfiguration::class,
        MongoAutoConfiguration::class,
        MongoDataAutoConfiguration::class,
        MongoReactiveAutoConfiguration::class,
        MongoReactiveDataAutoConfiguration::class,
        MongoReactiveRepositoriesAutoConfiguration::class,
        MongoRepositoriesAutoConfiguration::class,
        MustacheAutoConfiguration::class,
        Neo4jAutoConfiguration::class,
        Neo4jDataAutoConfiguration::class,
        Neo4jReactiveDataAutoConfiguration::class,
        Neo4jReactiveRepositoriesAutoConfiguration::class,
        Neo4jRepositoriesAutoConfiguration::class,
        OAuth2ResourceServerAutoConfiguration::class,
        QuartzAutoConfiguration::class,
        R2dbcAutoConfiguration::class,
        R2dbcDataAutoConfiguration::class,
        R2dbcRepositoriesAutoConfiguration::class,
        R2dbcTransactionManagerAutoConfiguration::class,
        RSocketMessagingAutoConfiguration::class,
        RSocketRequesterAutoConfiguration::class,
        RSocketSecurityAutoConfiguration::class,
        RSocketServerAutoConfiguration::class,
        RSocketStrategiesAutoConfiguration::class,
        RabbitAutoConfiguration::class,
        ReactiveElasticsearchRepositoriesAutoConfiguration::class,
        ReactiveOAuth2ClientAutoConfiguration::class,
        ReactiveOAuth2ResourceServerAutoConfiguration::class,
        ReactiveUserDetailsServiceAutoConfiguration::class,
        ReactiveWebServerFactoryAutoConfiguration::class,
        RedisAutoConfiguration::class,
        RedisReactiveAutoConfiguration::class,
        RedisRepositoriesAutoConfiguration::class,
        RepositoryRestMvcAutoConfiguration::class,
        Saml2RelyingPartyAutoConfiguration::class,
        SendGridAutoConfiguration::class,
        SessionAutoConfiguration::class,
        SpringApplicationAdminJmxAutoConfiguration::class,
        ThymeleafAutoConfiguration::class,
        WebFluxAutoConfiguration::class,
        WebSocketMessagingAutoConfiguration::class,
        WebSocketReactiveAutoConfiguration::class,
        XADataSourceAutoConfiguration::class,
    ]
)
@ConfigurationPropertiesScan
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
