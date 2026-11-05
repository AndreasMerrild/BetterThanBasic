package Fit;

import com.garmin.fit.Decoder;
import com.garmin.fit.FileIdMesg;
import com.garmin.fit.Mesg;
import com.garmin.fit.MesgListener;
import com.garmin.fit.RecordMesg;
import com.garmin.fit.SessionMesg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Parses activity FIT files using the official Garmin FIT Java SDK.
 */
public final class FitParser {
    public FitActivity parse(Path file) throws IOException {
        Objects.requireNonNull(file, "file");

        if (!Files.isRegularFile(file)) {
            throw new IOException("FIT file does not exist: " + file);
        }

        List<FitRecord> records = new ArrayList<>();
        ActivityMetadata metadata = new ActivityMetadata();

        byte[] contents = Files.readAllBytes(file);
        try {
            Decoder decoder = new Decoder(contents);
            decoder.addListener((MesgListener) mesg -> {
                if ("file_id".equals(mesg.getName())) {
                    metadata.accept(new FileIdMesg(mesg));
                } else if ("session".equals(mesg.getName())) {
                    metadata.accept(new SessionMesg(mesg));
                } else if ("record".equals(mesg.getName())) {
                    records.add(toRecord(new RecordMesg(mesg)));
                }
            });
            decoder.read();
        } catch (Exception exception) {
            String detail = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            throw new IOException("Could not parse FIT file " + file + ": " + detail, exception);
        }

        Instant start = records.isEmpty() ? null : records.get(0).timestamp();
        Instant end = records.isEmpty() ? null : records.get(records.size() - 1).timestamp();
        return new FitActivity(metadata.deviceName, start, end,
                metadata.functionalThresholdPower, records);
    }

    private static FitRecord toRecord(RecordMesg record) {
        Instant timestamp = record.getTimestamp() == null
                ? null
                : record.getTimestamp().getDate().toInstant();
        Integer power = record.getPower();
        Integer heartRate = record.getHeartRate() == null ? null : record.getHeartRate().intValue();
        Double speed = record.getSpeed() == null ? null : record.getSpeed().doubleValue();
        Integer cadence = record.getCadence() == null ? null : record.getCadence().intValue();
        return new FitRecord(timestamp, power, heartRate, speed, cadence);
    }

    private static final class ActivityMetadata {
        private String deviceName;
        private Integer functionalThresholdPower;

        private void accept(FileIdMesg fileId) {
            deviceName = fileId.getProductName();
        }

        private void accept(SessionMesg session) {
            if (functionalThresholdPower == null) {
                functionalThresholdPower = session.getThresholdPower();
            }
        }
    }
}
