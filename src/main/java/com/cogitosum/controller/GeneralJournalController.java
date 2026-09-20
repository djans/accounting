package com.cogitosum.controller;

import com.cogitosum.entity.GeneralJournal;
import com.cogitosum.entity.JournalStatus;
import com.cogitosum.service.GeneralJournalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/journals")
public class GeneralJournalController {
    
    @Autowired
    private GeneralJournalService generalJournalService;
    
    @PostMapping
    public ResponseEntity<GeneralJournal> createJournal(@RequestBody GeneralJournal journal) {
        try {
            GeneralJournal createdJournal = generalJournalService.createJournal(journal);
            return new ResponseEntity<>(createdJournal, HttpStatus.CREATED);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    @GetMapping
    public ResponseEntity<List<GeneralJournal>> getAllJournals() {
        try {
            List<GeneralJournal> journals = generalJournalService.getAllJournals();
            return new ResponseEntity<>(journals, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<GeneralJournal> getJournalById(@PathVariable Long id) {
        try {
            Optional<GeneralJournal> journal = generalJournalService.getJournalById(id);
            return journal.map(j -> new ResponseEntity<>(j, HttpStatus.OK))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/number/{journalNumber}")
    public ResponseEntity<GeneralJournal> getJournalByNumber(@PathVariable String journalNumber) {
        try {
            Optional<GeneralJournal> journal = generalJournalService.getJournalByNumber(journalNumber);
            return journal.map(j -> new ResponseEntity<>(j, HttpStatus.OK))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/status/{status}")
    public ResponseEntity<List<GeneralJournal>> getJournalsByStatus(@PathVariable JournalStatus status) {
        try {
            List<GeneralJournal> journals = generalJournalService.getJournalsByStatus(status);
            return new ResponseEntity<>(journals, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/date-range")
    public ResponseEntity<List<GeneralJournal>> getJournalsByDateRange(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        try {
            List<GeneralJournal> journals = generalJournalService.getJournalsByDateRange(startDate, endDate);
            return new ResponseEntity<>(journals, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @GetMapping("/posted/date-range")
    public ResponseEntity<List<GeneralJournal>> getPostedJournalsByDateRange(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        try {
            List<GeneralJournal> journals = generalJournalService.getPostedJournalsByDateRange(startDate, endDate);
            return new ResponseEntity<>(journals, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<GeneralJournal> updateJournal(@PathVariable Long id, @RequestBody GeneralJournal journal) {
        try {
            GeneralJournal updatedJournal = generalJournalService.updateJournal(id, journal);
            if (updatedJournal != null) {
                return new ResponseEntity<>(updatedJournal, HttpStatus.OK);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    @PutMapping("/{id}/post")
    public ResponseEntity<GeneralJournal> postJournal(
            @PathVariable Long id,
            @RequestParam String postedBy) {
        try {
            GeneralJournal journal = generalJournalService.postJournal(id, postedBy);
            if (journal != null) {
                return new ResponseEntity<>(journal, HttpStatus.OK);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    @PutMapping("/{id}/reverse")
    public ResponseEntity<GeneralJournal> reverseJournal(
            @PathVariable Long id,
            @RequestParam String reason) {
        try {
            GeneralJournal journal = generalJournalService.reverseJournal(id, reason);
            if (journal != null) {
                return new ResponseEntity<>(journal, HttpStatus.OK);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteJournal(@PathVariable Long id) {
        try {
            generalJournalService.deleteJournal(id);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}

