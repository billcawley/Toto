package com.azquo.admin.user;

import com.azquo.admin.StandardEntity;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class UserEvent extends StandardEntity {
    

    /**
     * Copyright (C) 2022 Azquo Holdings Ltd.
     * <p>
     * Created by WFC on 22/02/22
     * An entry for event on a report with recording turned on
     */

        final private LocalDateTime date;
        final private int businessId;
         final private int userId;
         final private int reportId;
         private String event;

        public UserEvent(int id, LocalDateTime date, int businessId,  int userId, int reportId, String event) {
            this.id = id;
            this.date = date;
            this.businessId = businessId;
            this.userId = userId;
            this.reportId = reportId;
            this.event = event;
         }

        public LocalDateTime getDate() {
            return date;
        }

        public int getBusinessId() {
            return businessId;
        }

           public int getUserId() {
            return userId;
        }

         public int getReportId() {
            return reportId;
        }

    public String getEvent() {
            return event;
        }


        @Override
        public String toString() {
            return "UserEvent{" +
                    "date=" + date +
                    ", businessId=" + businessId +
                    ", userId=" + userId +
                    ", reportId='" + reportId + '\'' +
                    ", event='" + event + '\'' +
                    ", id=" + id +
                    '}';
        }

        // Saw for JSON, now JSTL, need the getters

        public static class UserEventForDisplay {
            public final int id;
            public final LocalDateTime date;
            final String userName;
            final String reportName;
            final String event;

            public UserEventForDisplay(UserEvent ur, String userName, String reportName) {
                this.id = ur.id;
                this.date = ur.date;
                this.userName = userName;
                this.reportName = reportName;
                this.event = ur.event;
             }

            public LocalDateTime getDate() {
                return date;
            }

            static DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yy-HH:mm");

            static DateTimeFormatter df2 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

            public String getFormattedDate() {
                return df.format(date);
            }

            public String getTextOrderedDate() {
                return df2.format(date);
            }

            public String getUserName() {
                return userName;
            }

            public String getEvent() {
                return event;
            }

            public int getId() {
                return id;
            }
        }
    }
    

