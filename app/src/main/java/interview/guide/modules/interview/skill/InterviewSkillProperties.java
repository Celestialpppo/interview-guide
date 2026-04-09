package interview.guide.modules.interview.skill;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.interview.skill")
public class InterviewSkillProperties {

    /**
     * 单个预设 Skill 的结构定义，对应 resources/skills/*.yml 文件
     */
    public static class SkillDefinition {
        private String name;
        private String description;
        private String persona;
        private DisplayDef display;
        private List<CategoryDef> categories = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getPersona() { return persona; }
        public void setPersona(String persona) { this.persona = persona; }
        public DisplayDef getDisplay() { return display; }
        public void setDisplay(DisplayDef display) { this.display = display; }
        public List<CategoryDef> getCategories() { return categories; }
        public void setCategories(List<CategoryDef> categories) { this.categories = categories; }
    }

    public static class DisplayDef {
        private String icon;
        private String gradient;
        private String iconBg;
        private String iconColor;

        public String getIcon() { return icon; }
        public void setIcon(String icon) { this.icon = icon; }
        public String getGradient() { return gradient; }
        public void setGradient(String gradient) { this.gradient = gradient; }
        public String getIconBg() { return iconBg; }
        public void setIconBg(String iconBg) { this.iconBg = iconBg; }
        public String getIconColor() { return iconColor; }
        public void setIconColor(String iconColor) { this.iconColor = iconColor; }
    }

    public static class CategoryDef {
        private String key;
        private String label;
        private String priority;

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
    }
}
