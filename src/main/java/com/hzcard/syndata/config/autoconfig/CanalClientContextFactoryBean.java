package com.hzcard.syndata.config.autoconfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import com.alibaba.otter.canal.client.CanalConnectors;
import com.hzcard.syndata.datadeal.AbstractCanalClientScala;

import akka.actor.ActorSystem;

/**
 * Created by zhangwei on 2017/3/6.
 */
@Component
@DependsOn({"mapDataSource","actorSystem"})
public class CanalClientContextFactoryBean extends AbstractFactoryBean<CanalClientContext> implements ApplicationContextAware{

    private ApplicationContext applicationContext;

    private Logger logger= LoggerFactory.getLogger(CanalClientContextFactoryBean.class);

    @Autowired
    private CanalClientProperties canalClientProperties;

    @Autowired
    private ActorSystem actorSystem;


    @Override
    public Class<?> getObjectType() {
        return CanalClientContext.class;
    }

    @Override
    protected CanalClientContext createInstance() throws Exception {
        CanalClientContext context = new CanalClientContext(this.applicationContext, canalClientProperties);
        for (String schema : canalClientProperties.getSchemas()
                .keySet()) {
            if (canalClientProperties.getSchemas().get(schema).getTableRepository() != null)
                for (String tableName : canalClientProperties.getSchemas().get(schema).getTableRepository().keySet()) {
                    if (canalClientProperties.getSchemas().get(schema).getTableRepository().get(tableName).getRepository() != null) {
                        Class repositoryClass = null;
                        try {
                            repositoryClass = ClassUtils.forName(canalClientProperties.getSchemas().get(schema).getTableRepository().get(tableName).getRepository(),
                                    applicationContext.getClassLoader());
                        } catch (ClassNotFoundException e) {
                            logger.error("markContext exception ,not found reporsitory", e);
                            throw e;
                        } catch (LinkageError e) {
                            logger.error("markContext exception ,LinkageError", e);
                            throw e;
                        }
                        Class repositoryClassed = context.regiestResetEndPoint(canalClientProperties.getSchemas().get(schema).getTableRepository().get(tableName).getRepository(), repositoryClass);
                        if (!repositoryClassed.equals(repositoryClass))
                            throw new RuntimeException("the same repository config!");
                    }
                }
        }

        for (String destination : canalClientProperties.getDestinations().keySet()) {
            validate(canalClientProperties);
            validate(canalClientProperties.getDestinations().get(destination));

          AbstractCanalClientScala client  = new AbstractCanalClientScala(destination, CanalConnectors.newClusterConnector(canalClientProperties.getZkServers(), destination, canalClientProperties.getDestinations().get(destination).getUserName(), canalClientProperties.getDestinations().get(destination).getPassword()), actorSystem, context, applicationContext);
//          AbstractCanalClient client  = new SimpleCanalClient(destination, CanalConnectors.newClusterConnector(canalClientProperties.getZkServers(), destination, canalClientProperties.getDestinations().get(destination).getUserName(), canalClientProperties.getDestinations().get(destination).getPassword()), context);
          logger.error("init client: {}",client);
          context.regiestSimpleCanalClient(destination,client);
          
        } 
        context.startClient();
        return context;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private void validate(CanalClientProperties canalClientProperties) {
        Assert.notNull(canalClientProperties.getDestinations(), "Destinations must not null");
        Assert.notEmpty(canalClientProperties.getSchemas(), "table Repository must not null");
    }

    private void validate(DestinationProperty destinationProperty) {
        Assert.notNull(destinationProperty.getIncludeSchemas(), "includeSchemas must not null");
//		Assert.notNull(destinationProperty.getInstanceConfig(), "InstanceConfig not null");
    }

    @Override
    protected void destroyInstance(CanalClientContext instance) throws Exception {
        instance.destroy();
    }
}
