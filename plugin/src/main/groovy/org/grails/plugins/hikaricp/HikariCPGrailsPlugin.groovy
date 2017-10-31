package org.grails.plugins.hikaricp

import com.zaxxer.hikari.HikariDataSource
import grails.config.Config
import grails.plugins.Plugin
import grails.util.Environment
import groovy.util.logging.Slf4j
import org.grails.core.exceptions.GrailsConfigurationException
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy
import org.springframework.util.ClassUtils

@Slf4j
class HikariCPGrailsPlugin extends Plugin {
	def grailsVersion = "3.2.11 > *"

	def title = "Hikari CP" // Headline display name of the plugin
	def author = "Sudhir Nimavat"
	def description = "Hikari Connection pool for Grails"

	def loadAfter = ['dataSource']

	Closure doWithSpring() {
		{ ->
			Config config = grailsApplication.config

			def defaultDataSource = config.getProperty('dataSource', Map)
			def dataSources = config.getProperty('dataSources', Map, [:])
			if (defaultDataSource) {
				dataSources['dataSource'] = defaultDataSource
			}

			for (Map.Entry<String, Object> entry in dataSources.entrySet()) {
				def name = entry.key
				def value = entry.value
				if (value instanceof Map) {
					createDatasource delegate, name, (Map) value
				}
			}

		}
	}


	protected void createDatasource(beanBuilder, String dataSourceName, Map dataSourceData) {
		boolean isDefault = dataSourceName == 'dataSource'
		String suffix = isDefault ? '' : "_$dataSourceName"
		String unproxiedName = "dataSourceUnproxied$suffix"
		String lazyName = "dataSourceLazy$suffix"
		String beanName = isDefault ? 'dataSource' : "dataSource_$dataSourceName"


		boolean readOnly = Boolean.TRUE.equals(dataSourceData.readOnly)
		boolean pooled = !Boolean.FALSE.equals(dataSourceData.pooled)

		//if it is not a pooled datasource, just return, and use grails default datasources.
		if(!pooled) return

		String driver = dataSourceData?.driverClassName ?: "org.h2.Driver"

		final String hsqldbDriver = "org.hsqldb.jdbcDriver"
		if (hsqldbDriver.equals(driver) && !ClassUtils.isPresent(hsqldbDriver, getClass().classLoader)) {
			throw new GrailsConfigurationException("Database driver [" + hsqldbDriver +
					"] for HSQLDB not found. Since Grails 2.0 H2 is now the default database. You need to either " +
					"add the 'org.h2.Driver' class as your database driver and change the connect URL format " +
					"(for example 'jdbc:h2:mem:devDb') in DataSource.groovy or add HSQLDB as a dependency of your application.")
		}

		boolean defaultDriver = (driver == "org.h2.Driver")

		String pwd
		boolean passwordSet = false
		if (dataSourceData.password) {
			pwd = dataSourceData.password
			passwordSet = true
		} else if (defaultDriver) {
			pwd = ''
			passwordSet = true
		}

		beanBuilder."abstractGrailsDataSourceBean$suffix" {
			driverClassName = driver
			readOnly = readOnly

			jdbcUrl = dataSourceData.url ?: "jdbc:h2:mem:grailsDB;MVCC=TRUE;LOCK_TIMEOUT=10000"

			String theUsername = dataSourceData.username ?: (defaultDriver ? "sa" : null)

			if (theUsername != null) {
				username = theUsername
			}

			if (passwordSet) password = pwd

			// support for setting custom properties (for example maxActive) on the dataSource bean
			def dataSourceProperties = dataSourceData.properties
			if (dataSourceProperties) {
				if (dataSourceProperties instanceof Map) {
					for (entry in dataSourceProperties) {
						log.debug("Setting property on dataSource bean $entry.key -> $entry.value")
						delegate."${entry.key}" = entry.value
					}
				} else {
					log.warn("dataSource.properties is not an instanceof java.util.Map, ignoring")
				}
			}
		}

		def parentConfig = { dsBean ->
			dsBean.parent = 'abstractGrailsDataSourceBean' + suffix
		}

		String desc = isDefault ? 'data source' : "data source '$dataSourceName'"
		log.info "[RuntimeConfiguration] Configuring $desc for environment: $Environment.current"

		Class dsClass = HikariDataSource

		def bean = beanBuilder."$unproxiedName"(dsClass, parentConfig)
		bean.destroyMethod = "close"

		beanBuilder."$lazyName"(LazyConnectionDataSourceProxy, beanBuilder.ref(unproxiedName))
		beanBuilder."$beanName"(TransactionAwareDataSourceProxy, beanBuilder.ref(lazyName))
	}
}
