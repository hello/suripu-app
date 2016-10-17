package com.hello.suripu.app.models;

import com.google.common.base.Optional;
import com.hello.suripu.app.utils.SerialNumberUtils;
import com.hello.suripu.core.db.KeyStore;
import com.hello.suripu.core.models.DeviceKeyStoreRecord;
import com.hello.suripu.core.models.device.v2.Sense;
import com.hello.suripu.core.sense.metadata.SenseMetadata;
import com.hello.suripu.core.sense.metadata.SenseMetadataDAO;

public class MetadataDAODynamoDB implements SenseMetadataDAO {

    private final KeyStore keyStore;

    public MetadataDAODynamoDB(final KeyStore keyStore) {
        this.keyStore = keyStore;
    }

    @Override
    public SenseMetadata get(String s) {

        final Optional<DeviceKeyStoreRecord> recordFromDDB = keyStore.getKeyStoreRecord(s);
        if(!recordFromDDB.isPresent()) {
            return SenseMetadata.unknown(s);
        }

        final DeviceKeyStoreRecord record = recordFromDDB.get();
        final Optional<Sense.Color> colorOptional = SerialNumberUtils.extractColorFrom(record.metadata);
        return SenseMetadata.create(s, colorOptional.or(Sense.Color.BLACK), record.hardwareVersion, null);
    }

    @Override
    public Integer put(SenseMetadata senseMetadata) {
        return null;
    }
}
