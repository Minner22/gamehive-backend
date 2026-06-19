ALTER TABLE user_profiles DROP COLUMN address;
ALTER TABLE user_profiles ADD COLUMN street      VARCHAR(255);
ALTER TABLE user_profiles ADD COLUMN city        VARCHAR(255);
ALTER TABLE user_profiles ADD COLUMN postal_code VARCHAR(20);
ALTER TABLE user_profiles ADD COLUMN country     VARCHAR(100);