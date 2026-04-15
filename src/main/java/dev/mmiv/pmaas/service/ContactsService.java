package dev.mmiv.pmaas.service;

import dev.mmiv.pmaas.dto.ContactFilterRequest;
import dev.mmiv.pmaas.dto.ContactRequest;
import dev.mmiv.pmaas.dto.ContactResponse;
import dev.mmiv.pmaas.dto.ImportResponse;
import dev.mmiv.pmaas.entity.Contact;
import dev.mmiv.pmaas.repository.ContactRepository;
import dev.mmiv.pmaas.specification.ContactSpecification;
import dev.mmiv.pmaas.entity.Patients;
import dev.mmiv.pmaas.repository.PatientsRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactsService {

    private final ContactRepository     contactRepository;
    private final PatientsRepository    patientsRepository;
    private final ContactImportService  importService;
    private final ContactExportService  exportService;

    // READ — paginated, filtered list

    @Transactional(readOnly = true)
    public Page<ContactResponse> findAll(ContactFilterRequest filter, Pageable pageable) {
        Specification<Contact> spec = ContactSpecification.from(filter);
        return contactRepository.findAll(spec, pageable)
                .map(ContactResponse::from);
    }

    // READ — single record

    @Transactional(readOnly = true)
    public ContactResponse findById(Long id) {
        return contactRepository.findById(id)
                .map(ContactResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Contact with id " + id + " not found."));
    }

    // CREATE

    @Transactional
    public ContactResponse create(ContactRequest request) {
        Contact contact = mapToEntity(request, new Contact());
        Contact saved = contactRepository.save(contact);
        log.info("Contact created: id={}, name={}", saved.getId(), saved.getName());
        return ContactResponse.from(saved);
    }

    // UPDATE

    @Transactional
    public ContactResponse update(Long id, ContactRequest request) {
        Contact existing = contactRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Contact with id " + id + " not found."));

        mapToEntity(request, existing);
        Contact saved = contactRepository.save(existing);
        log.info("Contact updated: id={}, name={}", saved.getId(), saved.getName());
        return ContactResponse.from(saved);
    }

    // DELETE

    @Transactional
    public void delete(Long id) {
        if (!contactRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Contact with id " + id + " not found.");
        }
        contactRepository.deleteById(id);
        log.info("Contact deleted: id={}", id);
    }

    // IMPORT

    @Transactional
    public ImportResponse importFromExcel(MultipartFile file) throws IOException {
        return importService.importContacts(file);
    }

    // EXPORT

    @Transactional(readOnly = true)
    public void exportToExcel(
            ContactFilterRequest filter,
            HttpServletResponse response
    ) throws IOException {
        Specification<Contact> spec = ContactSpecification.from(filter);
        exportService.exportContacts(spec, response);
    }

    // Internal helpers

    /**
     * Maps a ContactRequest onto a Contact entity in-place.
     * Used for both create (new Contact()) and update (existing entity).
     * Resolves the optional patient association by ID.
     */
    private Contact mapToEntity(ContactRequest request, Contact contact) {
        contact.setContactDate(request.contactDate());
        contact.setContactTime(request.contactTime());
        contact.setName(request.name().trim());
        contact.setDesignation(request.designation());
        contact.setVisitType(request.visitType());
        contact.setContactNumber(request.contactNumber());
        contact.setModeOfCommunication(request.modeOfCommunication());
        contact.setPurpose(request.purpose());
        contact.setRemarks(request.remarks());
        contact.setRespond(request.respond());

        if (request.patientId() != null) {
            Patients patient = patientsRepository.findById(Math.toIntExact(request.patientId()))
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Patient with id " + request.patientId() + " not found."));
            contact.setPatient(patient);
        } else {
            contact.setPatient(null);
        }

        return contact;
    }
}