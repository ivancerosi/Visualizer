package algebra.diplomski.ivan.controller;

import algebra.diplomski.ivan.controller.service.GraphService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.tinkerpop.blueprints.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

@RestController
public class Controller {
    private static Logger EXCEPTIONS_LOGGER =
            LoggerFactory.getLogger("exceptions");

    @Autowired
    GraphService graphService;

    static ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();

    @GetMapping("/hello")
    public String hello() {
        return "Hello";
    }



    @GetMapping("/methodsByClass/{classId}")
    public ResponseEntity<?> getMethodsByClass(@PathVariable String classId) {
        if (classId.contains("\"") || classId.contains("\'") || classId.contains("(")) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(graphService.getMethodsByClass("#"+classId));
    }

    @GetMapping("/method")
    public ResponseEntity<?> getMethods() {
        return ResponseEntity.ok(graphService.getMethodList(null));
    }

    @GetMapping("/method/{substring}")
    public ResponseEntity<?> getMethods(@PathVariable String substring) {
        if (substring.contains("\"") || substring.contains("\'") || substring.contains("(")) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(graphService.getMethodList(substring));
    }

    @GetMapping("/class")
    public ResponseEntity<?> getClassesNoSubstring() {
        return ResponseEntity.ok(graphService.getClassList(null));
    }

    @GetMapping("/class/{substring}")
    public ResponseEntity<?> getClasses(@PathVariable String substring) {
        // weird characters in class/method name
        if (substring.contains("\"") || substring.contains("\'") || substring.contains("(")) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(graphService.getClassList(substring));
    }

    @GetMapping("/traverse/{resolution}/{direction}/{id}")
    public ResponseEntity<?> get(@PathVariable  String resolution,
                                 @PathVariable String direction,
                                 @PathVariable String id,
                                 @RequestParam(required = false) String limit
                                 ) {

        resolution = resolution.toLowerCase();
        direction = direction.toUpperCase();
        if (!(resolution.equals("class") || resolution.equals("method"))) {
            return ResponseEntity.badRequest().body("'resolution' path variable has to be either a class or a method");
        }
        if (!(direction.equals("IN") || direction.equals("OUT"))) {
            return ResponseEntity.badRequest().body("'resolution' path variable has to be either a class or a method");
        }
        int queryLimit=50;
        if (limit!=null) {
            try {
                queryLimit = Integer.parseInt(limit);
                if (queryLimit<=0) throw new Exception();
            } catch (Exception e) {
                return ResponseEntity.badRequest().body("'limit' query parameter is not a positive integer");
            }
        }
        try {
            return ResponseEntity.ok(graphService.traverseFromNode(id,resolution, Direction.valueOf(direction),queryLimit));
        } catch (Exception e) {
            EXCEPTIONS_LOGGER.error("Request exception: "+e.toString());
            return ResponseEntity.internalServerError().build();
        }
    }
}
