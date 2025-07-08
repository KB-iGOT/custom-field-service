package com.igot.cb.transactional.cassandrautils;


import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.pores.util.PropertiesCache;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CassandraConnectionManagerImpl2Test {

    @Test
    void testCreateCassandraConnectionWithKeySpaces_success() throws Exception {
        CqlSession mockSession = mock(CqlSession.class);
        CqlSessionBuilder mockBuilder = mock(CqlSessionBuilder.class);

        when(mockBuilder.addContactPoints(any())).thenReturn(mockBuilder);
        when(mockBuilder.withLocalDatacenter(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.withKeyspace(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.withConfigLoader(any())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockSession);

        Node mockNode = mock(Node.class);
        when(mockNode.getDatacenter()).thenReturn("dc1");
        when(mockNode.getRack()).thenReturn("rack1");

        Map<UUID, Node> nodesMap = new HashMap<>();
        nodesMap.put(UUID.randomUUID(), mockNode);
        Metadata mockMetadata = mock(Metadata.class);
        when(mockSession.getMetadata()).thenReturn(mockMetadata);
        when(mockMetadata.getClusterName()).thenReturn("TestCluster".describeConstable());
        when(mockMetadata.getNodes()).thenReturn(nodesMap);

        try (MockedStatic<CqlSession> staticCqlSession = mockStatic(CqlSession.class);
             MockedStatic<PropertiesCache> staticCache = mockStatic(PropertiesCache.class)) {

            staticCqlSession.when(CqlSession::builder).thenReturn(mockBuilder);

            PropertiesCache mockCache = mock(PropertiesCache.class);
            when(mockCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("127.0.0.1");
            when(mockCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_LOCAL)).thenReturn("1");
            when(mockCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_REMOTE)).thenReturn("1");
            when(mockCache.getProperty(Constants.HEARTBEAT_INTERVAL)).thenReturn("30");
            staticCache.when(PropertiesCache::getInstance).thenReturn(mockCache);

            CassandraConnectionManagerImpl manager = new CassandraConnectionManagerImpl();

            Method method = CassandraConnectionManagerImpl.class
                    .getDeclaredMethod("createCassandraConnectionWithKeySpaces", String.class);
            method.setAccessible(true);

            Object result = method.invoke(manager, "test_keyspace");

            assertNotNull(result);
            assertEquals(mockSession, result);
        }
    }
}

