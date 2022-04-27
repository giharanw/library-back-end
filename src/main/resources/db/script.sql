create table book(
    isbn varchar(25) primary key,
    name varchar(100) not null,
    author varchar(100) not null,
    preview longblob
);

CREATE TABLE member(
    nic VARCHAR(10) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    contact VARCHAR(15) NOT NULL
);

CREATE TABLE issue(
    id INT PRIMARY KEY AUTO_INCREMENT,
    nic VARCHAR(100) NOT NULL,
    isbn VARCHAR(25) NOT NULL,
    date DATE NOT NULL,
    CONSTRAINT fk_member FOREIGN KEY (nic) REFERENCES member (nic),
    CONSTRAINT fK_book FOREIGN KEY (isbn) REFERENCES book (isbn)
);