package algebra.diplomski.ivan.controller.dao.dto;

import com.tinkerpop.blueprints.Vertex;
import lombok.Data;
import org.json.JSONObject;

@Data
public class NodeDto {
    String id;
    String name;
    String group;
    public NodeDto(Vertex vertex) {
        this.id = vertex.getId().toString();
        this.name=vertex.getProperty("name");
        this.group=vertex.getProperty("@class");
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof NodeDto) {
            return (((NodeDto) other).getId().equals(id));
        } else return false;
    }

    public JSONObject toJson() {
        JSONObject result = new JSONObject();
        result.put("id",id);
        result.put("name",name);
        result.put("group",group);
        return result;
    }
}
