
/**
 * Copyright (C) 2022 Azquo Holdings Ltd.
 * <p>
 */


package com.azquo.dataimport;

public class ImportStage {
    String stageClass;
    String stageNumber;
    String stageName;
    String stageComment;
    String fields;
    String fieldHeadings;
    String instructions;
    String suggestions;
    String typeName;
    String dataParent;//only for stage 3
    String title;

    ImportStage(int currentStage, int  stageNumber, String stageName){
        if (currentStage > stageNumber){
            this.stageClass = "complete";
            this.stageNumber = "tick";
        }else if(currentStage==stageNumber){
            this.stageClass="current";
            this.stageNumber = stageNumber + "";
        }else{
            this.stageClass="upcoming";
            this.stageNumber = stageNumber+"";
        }
        this.stageName = stageName;
        this.stageComment = "";
        this.fields = "";
        this.fieldHeadings = "";
        this.instructions = "";
        this.suggestions = "";
        this.typeName = "";
        this.dataParent = null;
        this.title = "";
    }

    String getStageNumber(){return stageNumber; }

    String getStageName(){return  stageName; }

    void setStageComment(String stageComment){this.stageComment = stageComment; }

    String getStageComment(){return  stageComment; }

    void setFields(String fields){this.fields = fields; }

    String getFields(){return fields; }

    void setFieldHeadings(String fieldHeadings){this.fieldHeadings = fieldHeadings; }

    String getFieldHeadings(){return fieldHeadings; }

    void setInstructions(String instructions){this.instructions = instructions; }

    String getInstructions(){return instructions; };

    void setSuggestions(String suggestions){this.suggestions = suggestions; }

    String getSuggestions(){return suggestions; }

    void setTypeName(String typeName){this.typeName = typeName; }

    String getTypeName(){return  typeName; }

    void setDataParent(String dataParent){this.dataParent = dataParent; }

    String getDataParent(){return dataParent; }

    void setTitle(String title){this.title = title; }

    String getTitle(){return title; }
}

