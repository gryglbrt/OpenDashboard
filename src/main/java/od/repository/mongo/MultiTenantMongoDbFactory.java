/**
 * https://github.com/Loki-Afro/multi-tenant-spring-mongodb/blob/master/src/main/java/com/github/zarathustra/mongo/MultiTenantMongoDbFactory.java
 * 
 * 
 */

package od.repository.mongo;
import java.util.HashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoDbFactory;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver.IndexDefinitionHolder;
import org.springframework.data.mongodb.core.mapping.BasicMongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.util.Assert;

import com.mongodb.DB;
import com.mongodb.Mongo;

public class MultiTenantMongoDbFactory extends SimpleMongoDbFactory {

    private final String defaultName;
    private static final Logger logger = LoggerFactory.getLogger(MultiTenantMongoDbFactory.class);

    private static final ThreadLocal<String> dbName = new ThreadLocal<String>();
    private static final HashMap<String, Object> databaseIndexMap = new HashMap<String, Object>();
    private MongoTemplate mongoTemplate = null;

    public MultiTenantMongoDbFactory(final Mongo mongo, final String defaultDatabaseName) {
        super(mongo, defaultDatabaseName);
        logger.debug("Instantiating " + MultiTenantMongoDbFactory.class.getName() + " with default database name: " + defaultDatabaseName);
        this.defaultName = defaultDatabaseName;
    }

//    dirty but ... what can I do?
    public void setMongoTemplate(final MongoTemplate mongoTemplate) {
        Assert.isNull(this.mongoTemplate, "You can set MongoTemplate just once");
        this.mongoTemplate = mongoTemplate;
    }

    public static void setDatabaseNameForCurrentThread(final String databaseName) {
        logger.debug("Switching to database: " + databaseName);
        dbName.set(databaseName);
    }

    public static void clearDatabaseNameForCurrentThread() {
        if (logger.isDebugEnabled()) {
            logger.debug("Removing database [" + dbName.get() + "]");
        }
        dbName.remove();
    }

    @Override
    public DB getDb() {
        final String tlName = dbName.get();
        final String dbToUse = (tlName != null ? tlName : this.defaultName);
        logger.debug("Acquiring database: " + dbToUse);
        createIndexIfNecessaryFor(dbToUse);
        return super.getDb(dbToUse);
    }

    private void createIndexIfNecessaryFor(final String database) {
        if (this.mongoTemplate == null) {
            logger.error("MongoTemplate is null, will not create any index.");
            return;
        }
//        sync and init once
        boolean needsToBeCreated = false;
        synchronized (MultiTenantMongoDbFactory.class) {
            final Object syncObj = databaseIndexMap.get(database);
            if (syncObj == null) {
                databaseIndexMap.put(database, new Object());
                needsToBeCreated = true;
            }
        }
//        make sure only one thread enters with needsToBeCreated = true
        synchronized (databaseIndexMap.get(database)) {
            if (needsToBeCreated) {
                logger.debug("Creating indices for database name=[" + database + "]");
                createIndexes();
                logger.debug("Done with creating indices for database name=[" + database + "]");
            }
        }
    }

    private void createIndexes() {
        final MongoMappingContext mappingContext = (MongoMappingContext) this.mongoTemplate.getConverter().getMappingContext();
        final MongoPersistentEntityIndexResolver indexResolver = new MongoPersistentEntityIndexResolver(mappingContext);
        for (BasicMongoPersistentEntity<?> persistentEntity : mappingContext.getPersistentEntities()) {
            checkForAndCreateIndexes(indexResolver, persistentEntity);
        }
    }
    private void checkForAndCreateIndexes(final MongoPersistentEntityIndexResolver indexResolver, final MongoPersistentEntity<?> entity) {
//        make sure its a root document
        if (entity.findAnnotation(Document.class) != null) {
            for (IndexDefinitionHolder indexDefinitionHolder : indexResolver.resolveIndexForClass(entity.getType())) {
//                work because of javas reentered lock feature
                this.mongoTemplate.indexOps(entity.getType()).ensureIndex(indexDefinitionHolder);
            }
        }
    }
}