package interview.guide.modules.interview.skill;

import java.util.ArrayList;
import java.util.List;

public final class InterviewSkillProperties {

    private InterviewSkillProperties() {
    }

    /**
     * SKILL.md front matter（标准字段）。
     */
    public static class SkillFrontMatterDefinition {
        private String name;
        private String description;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    /**
     * skill.meta.yml（项目自定义字段）。
     */
    public static class SkillMetaDefinition {
        private String displayName;
        private DisplayDef display;
        private List<CategoryDef> categories = new ArrayList<>();

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public DisplayDef getDisplay() { return display; }
        public void setDisplay(DisplayDef display) { this.display = display; }
        public List<CategoryDef> getCategories() { return categories; }
        public void setCategories(List<CategoryDef> categories) { this.categories = categories; }
    }

    /**
     * 运行时聚合结构：标准字段 + 项目自定义字段。
     */
    public static class SkillDefinition {
        private String name;
        private String description;
        private String persona;
        private String displayName;
        private DisplayDef display;
        private List<CategoryDef> categories = new ArrayList<>();

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getPersona() { return persona; }
        public void setPersona(String persona) { this.persona = persona; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
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
        private String ref;
        private Boolean shared;

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public String getRef() { return ref; }
        public void setRef(String ref) { this.ref = ref; }
        public Boolean getShared() { return shared; }
        public void setShared(Boolean shared) { this.shared = shared; }
    }
}
