package com.example.voiceinput.controller;

import com.example.voiceinput.dao.NlpConfigDAO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/nlp")
public class NlpConfigController {

    private final NlpConfigDAO nlpConfigDAO;

    public NlpConfigController(NlpConfigDAO nlpConfigDAO) {
        this.nlpConfigDAO = nlpConfigDAO;
    }

    @GetMapping("/words")
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(nlpConfigDAO.findAll());
    }

    @PostMapping("/words")
    public ResponseEntity<?> add(@RequestBody Map<String, String> body) {
        String word = body.get("word");
        String category = body.get("category");
        String intent = body.getOrDefault("intent", "");
        if (word == null || word.isBlank() || category == null || category.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "word 和 category 必填"));
        }
        nlpConfigDAO.insert(word, category, intent);
        return ResponseEntity.ok(Map.of("success", true, "word", word));
    }

    @DeleteMapping("/words/{id}")
    public ResponseEntity<?> delete(@PathVariable int id) {
        boolean ok = nlpConfigDAO.delete(id);
        return ResponseEntity.ok(Map.of("success", ok));
    }
}
