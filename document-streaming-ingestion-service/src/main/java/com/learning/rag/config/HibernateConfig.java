package com.learning.rag.config;


import org.hibernate.type.SqlTypes;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HibernateConfig {

    /**
     * Register custom JDBC type for pgvector
     */
    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return hibernateProperties -> {
            hibernateProperties.put(
                "hibernate.type.preferred_jdbc_type_for_array", 
                SqlTypes.ARRAY
            );
        };
    }
}