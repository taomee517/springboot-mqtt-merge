/**
 * Copyright (c) 2018, Mr.Wang (recallcode@aliyun.com) All rights reserved.
 */

package org.example.mqtt.server.config;

import org.apache.commons.lang3.StringUtils;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteMessaging;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.cluster.ClusterState;
import org.apache.ignite.configuration.*;
import org.apache.ignite.logger.log4j2.Log4J2Logger;
import org.apache.ignite.spi.discovery.DiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.multicast.TcpDiscoveryMulticastIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 自动配置apache ignite
 */
@Configuration
public class IgniteAutoConfig {

	@Autowired
	IgniteProperties igniteProperties;


	@Bean
	public Ignite ignite() throws Exception {
		IgniteConfiguration igniteConfiguration = new IgniteConfiguration();
		// Ignite实例名称
		igniteConfiguration.setIgniteInstanceName(igniteProperties.getBrokerId());
		// Ignite日志
		igniteConfiguration.setGridLogger(new Log4J2Logger(igniteProperties.getLoggerPath()));
		//配置数据区域
		igniteConfiguration.setDataStorageConfiguration(dataStorageConfiguration());
		//2.9.0版本，因集群搭建失败新增配置
		igniteConfiguration.setPeerClassLoadingEnabled(true);
		igniteConfiguration.setMetricsLogFrequency(0);
		igniteConfiguration.setLifecycleBeans(new IgniteLifeCycleBean());
		// 集群, 基于组播或静态IP配置
		igniteConfiguration.setDiscoverySpi(discoverySpi());
		Ignite ignite = Ignition.start(igniteConfiguration);
		//2.9.0版本 由ignite.cluster().active(true)变更而来
		ignite.cluster().state(ClusterState.ACTIVE);
		return ignite;
	}

	private DataStorageConfiguration dataStorageConfiguration(){
		// 非持久化数据区域
		DataRegionConfiguration notPersistence = new DataRegionConfiguration().setPersistenceEnabled(false)
				.setInitialSize(igniteProperties.getNotPersistenceInitialSize() * 1024 * 1024)
				.setMaxSize(igniteProperties.getNotPersistenceMaxSize() * 1024 * 1024).setName("not-persistence-data-region");
		// 持久化数据区域
		DataRegionConfiguration persistence = new DataRegionConfiguration().setPersistenceEnabled(true)
				.setInitialSize(igniteProperties.getPersistenceInitialSize() * 1024 * 1024)
				.setMaxSize(igniteProperties.getPersistenceMaxSize() * 1024 * 1024).setName("persistence-data-region");
		DataStorageConfiguration dataStorageConfiguration = new DataStorageConfiguration().setDefaultDataRegionConfiguration(notPersistence)
				.setDataRegionConfigurations(persistence)
				.setWalArchivePath(StringUtils.isNotBlank(igniteProperties.getPersistenceStorePath()) ? igniteProperties.getPersistenceStorePath() : null)
				.setWalPath(StringUtils.isNotBlank(igniteProperties.getPersistenceStorePath()) ? igniteProperties.getPersistenceStorePath() : null)
				.setStoragePath(StringUtils.isNotBlank(igniteProperties.getPersistenceStorePath()) ? igniteProperties.getPersistenceStorePath() : null);
		return dataStorageConfiguration;
	}

	private CacheConfiguration shortCacheConfiguration(){
		CacheConfiguration cacheConfigClass = new CacheConfiguration();
		cacheConfigClass.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);
		cacheConfigClass.setCacheMode(CacheMode.REPLICATED);
		cacheConfigClass.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_ASYNC);
		cacheConfigClass.setName("short-cache");
		cacheConfigClass.setDataRegionName("short-cache-region");
		return cacheConfigClass;
	}

	private TransactionConfiguration transactionConfiguration(){
		TransactionConfiguration transConfig = new TransactionConfiguration();
//		transConfig.setTxManagerFactory(FactoryBuilder.factoryOf(SpringTransactionManager.class));
		return transConfig;
	}

	private DiscoverySpi discoverySpi() {
//		TcpDiscoveryMulticastIpFinder ipFinder = new TcpDiscoveryMulticastIpFinder();
//		ipFinder.setAddresses(Collections.singletonList("127.0.0.1:47500..47509"));
//		return new TcpDiscoverySpi().setIpFinder(ipFinder);
		TcpDiscoverySpi tcpDiscoverySpi = new TcpDiscoverySpi();
		if (igniteProperties.isEnableMulticastGroup()) {
			TcpDiscoveryMulticastIpFinder tcpDiscoveryMulticastIpFinder = new TcpDiscoveryMulticastIpFinder();
			tcpDiscoveryMulticastIpFinder.setMulticastGroup(igniteProperties.getMulticastGroup());
			tcpDiscoverySpi.setIpFinder(tcpDiscoveryMulticastIpFinder);
		} else {
			TcpDiscoveryVmIpFinder tcpDiscoveryVmIpFinder = new TcpDiscoveryVmIpFinder();
			tcpDiscoveryVmIpFinder.setAddresses(igniteProperties.getStaticIpAddresses());
			tcpDiscoverySpi.setIpFinder(tcpDiscoveryVmIpFinder);
		}
		return tcpDiscoverySpi;
	}

	@Bean
	public IgniteCache messageIdCache() throws Exception {
		CacheConfiguration cacheConfiguration = new CacheConfiguration().setDataRegionName("not-persistence-data-region")
			.setCacheMode(CacheMode.PARTITIONED).setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL).setName("messageIdCache");
		return ignite().getOrCreateCache(cacheConfiguration);
	}

	@Bean
	public IgniteCache retainMessageCache() throws Exception {
		CacheConfiguration cacheConfiguration = new CacheConfiguration().setDataRegionName("persistence-data-region")
			.setCacheMode(CacheMode.PARTITIONED).setName("retainMessageCache");
		return ignite().getOrCreateCache(cacheConfiguration);
	}

	@Bean
	public IgniteCache subscribeNotWildcardCache() throws Exception {
		CacheConfiguration cacheConfiguration = new CacheConfiguration().setDataRegionName("persistence-data-region")
			.setCacheMode(CacheMode.PARTITIONED).setName("subscribeNotWildcardCache");
		return ignite().getOrCreateCache(cacheConfiguration);
	}

	@Bean
	public IgniteCache subscribeWildcardCache() throws Exception {
		CacheConfiguration cacheConfiguration = new CacheConfiguration().setDataRegionName("persistence-data-region")
			.setCacheMode(CacheMode.PARTITIONED).setName("subscribeWildcardCache");
		return ignite().getOrCreateCache(cacheConfiguration);
	}

	@Bean
	public IgniteCache dupPublishMessageCache() throws Exception {
		CacheConfiguration cacheConfiguration = new CacheConfiguration().setDataRegionName("persistence-data-region")
			.setCacheMode(CacheMode.PARTITIONED).setName("dupPublishMessageCache");
		return ignite().getOrCreateCache(cacheConfiguration);
	}

	@Bean
	public IgniteCache dupPubRelMessageCache() throws Exception {
		CacheConfiguration cacheConfiguration = new CacheConfiguration().setDataRegionName("persistence-data-region")
			.setCacheMode(CacheMode.PARTITIONED).setName("dupPubRelMessageCache");
		return ignite().getOrCreateCache(cacheConfiguration);
	}

	@Bean
	public IgniteMessaging igniteMessaging() throws Exception {
		return ignite().message(ignite().cluster().forRemotes());
	}

}
