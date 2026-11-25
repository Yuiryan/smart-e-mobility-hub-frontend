package vn.edu.bku.smart_emobility_backend.common.store;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import vn.edu.bku.smart_emobility_backend.common.domain.Student;

@Repository
public class InMemoryStudentDataStore implements StudentDataStore {
    private final InMemoryDataStore dataStore;

    public InMemoryStudentDataStore(InMemoryDataStore dataStore) { this.dataStore = dataStore; }

    @Override
    public Optional<Student> findById(String studentId) {
        return dataStore.findById(Student.class, studentId);
    }
}
