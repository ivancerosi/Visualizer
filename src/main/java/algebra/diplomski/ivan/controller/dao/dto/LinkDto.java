package algebra.diplomski.ivan.controller.dao.dto;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import lombok.Data;
import org.json.JSONObject;

@Data
public class LinkDto {
    String source;
    String target;
    String group;

    public LinkDto(Edge edge) {
        this.source = edge.getVertex(Direction.OUT).getId().toString();
        this.target = edge.getVertex(Direction.IN).getId().toString();
        this.group = edge.getLabel();
    }

    @Override
    public int hashCode() {
        return (source+target).hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof LinkDto) {
            return (other.hashCode()==hashCode());
        } else return false;
    }

    public JSONObject toJson() {
        JSONObject result = new JSONObject();
        result.put("source",source);
        result.put("target",target);
        result.put("group",group);
        return result;
    }
}
