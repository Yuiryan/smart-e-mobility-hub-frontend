package vn.edu.bku.smart_emobility_backend.common.store;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class JsonStore {
    private final JsonMapper mapper;
    private final Path dataFile;

    public JsonStore(JsonMapper mapper,
            @Value("${app.data-file:./data/data.json}") String dataFile) {
        this.mapper = mapper;
        this.dataFile = Path.of(dataFile);
    }

    public Optional<StoreSnapshot> load() {
        if (!Files.exists(dataFile)) return Optional.empty();
        try {
            String json = Files.readString(dataFile);
            return json.isBlank() ? Optional.empty()
                    : Optional.of(mapper.readValue(json, StoreSnapshot.class));
        } catch (IOException ex) {
            throw new IllegalStateException("Could not load data from " + dataFile, ex);
        }
    }

    public void save(StoreSnapshot snapshot) {
        Path target = dataFile.toAbsolutePath();
        Path parent = target.getParent();
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            if (parent != null) Files.createDirectories(parent);
            mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), snapshot);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Could not save data to " + dataFile, ex);
        }
    }
}
