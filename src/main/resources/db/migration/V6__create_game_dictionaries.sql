-- GH-114 (G1): tabele słownikowe fazy „Gry". Bez FK — relacje z Game dochodzą w #116.
CREATE TABLE IF NOT EXISTS publishers (
                                          id BIGSERIAL PRIMARY KEY,
                                          created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                          updated_at TIMESTAMP WITH TIME ZONE,
                                          name VARCHAR(255) NOT NULL UNIQUE,
                                          status VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS categories (
                                          id BIGSERIAL PRIMARY KEY,
                                          created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                          updated_at TIMESTAMP WITH TIME ZONE,
                                          name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS mechanics (
                                         id BIGSERIAL PRIMARY KEY,
                                         created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                         updated_at TIMESTAMP WITH TIME ZONE,
                                         name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS authors (
                                       id BIGSERIAL PRIMARY KEY,
                                       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                       updated_at TIMESTAMP WITH TIME ZONE,
                                       first_name VARCHAR(255) NOT NULL,
                                       last_name VARCHAR(255) NOT NULL,
                                       CONSTRAINT uq_authors_name UNIQUE (first_name, last_name)
);
