package com.dayz.sc.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "edupivot.storage")
public class StorageProperties {

    private final Minio minio = new Minio();
    private final Bucket bucket = new Bucket();
    private final Expiry expiry = new Expiry();
    private final Limits limits = new Limits();

    public Minio getMinio() {
        return minio;
    }

    public Bucket getBucket() {
        return bucket;
    }

    public Expiry getExpiry() {
        return expiry;
    }

    public Limits getLimits() {
        return limits;
    }

    public static class Minio {
        private String endpoint;
        private String externalEndpoint;
        private String accessKey;
        private String secretKey;
        private String region = "us-east-1";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getExternalEndpoint() {
            return externalEndpoint;
        }

        public void setExternalEndpoint(String externalEndpoint) {
            this.externalEndpoint = externalEndpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }
    }

    public static class Bucket {
        private String media = "edupivot-media";
        private String course = "edupivot-course";
        private String ai = "edupivot-ai";

        public String getMedia() {
            return media;
        }

        public void setMedia(String media) {
            this.media = media;
        }

        public String getCourse() {
            return course;
        }

        public void setCourse(String course) {
            this.course = course;
        }

        public String getAi() {
            return ai;
        }

        public void setAi(String ai) {
            this.ai = ai;
        }
    }

    public static class Expiry {
        private int uploadMinutes = 10;
        private int downloadMinutes = 30;

        public int getUploadMinutes() {
            return uploadMinutes;
        }

        public void setUploadMinutes(int uploadMinutes) {
            this.uploadMinutes = uploadMinutes;
        }

        public int getDownloadMinutes() {
            return downloadMinutes;
        }

        public void setDownloadMinutes(int downloadMinutes) {
            this.downloadMinutes = downloadMinutes;
        }
    }

    public static class Limits {
        private long avatarBytes = 2L * 1024 * 1024;
        private long courseCoverBytes = 5L * 1024 * 1024;
        private long forumImageBytes = 5L * 1024 * 1024;
        private long courseFileBytes = 200L * 1024 * 1024;
        private long aiFileBytes = 100L * 1024 * 1024;

        public long getAvatarBytes() {
            return avatarBytes;
        }

        public void setAvatarBytes(long avatarBytes) {
            this.avatarBytes = avatarBytes;
        }

        public long getCourseCoverBytes() {
            return courseCoverBytes;
        }

        public void setCourseCoverBytes(long courseCoverBytes) {
            this.courseCoverBytes = courseCoverBytes;
        }

        public long getForumImageBytes() {
            return forumImageBytes;
        }

        public void setForumImageBytes(long forumImageBytes) {
            this.forumImageBytes = forumImageBytes;
        }

        public long getCourseFileBytes() {
            return courseFileBytes;
        }

        public void setCourseFileBytes(long courseFileBytes) {
            this.courseFileBytes = courseFileBytes;
        }

        public long getAiFileBytes() {
            return aiFileBytes;
        }

        public void setAiFileBytes(long aiFileBytes) {
            this.aiFileBytes = aiFileBytes;
        }
    }
}
