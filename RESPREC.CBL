IDENTIFICATION DIVISION.
       PROGRAM-ID. RESPREC.
       AUTHOR. Agent.
       DATE-WRITTEN. 2024-07-15.
      *
       DATA DIVISION.
       FILE SECTION.
      *
       WORKING-STORAGE SECTION.
      *
       01 RESPONSE-REC.
          05 TOP-LEVEL-ERROR-MSG  PIC X(512).
          05 JOB-RESULTS-COUNT    PIC 9(4) COMP.
          05 JOB-RESULTS-TABLE OCCURS 0 TO 99 TIMES
                               DEPENDING ON JOB-RESULTS-COUNT.
             10 JR-HTML-LENGTH       PIC 9(9) COMP.
             10 JR-HTML-CONTENT      PIC X(4096).
             10 JR-SUCCESS-FLAG      PIC X(1).
                88 JR-SUCCESS        VALUE 'Y'.
                88 JR-FAILURE        VALUE 'N'.
             10 JR-ERROR-MSG         PIC X(512).
             10 JR-META-COUNT        PIC 9(4) COMP.
             10 JR-META-ITEMS OCCURS 0 TO 10 TIMES
                               DEPENDING ON JR-META-COUNT.
                15 JR-META-KEY       PIC X(50).
                15 JR-META-VALUE     PIC X(255).
             10 JR-ORIGINAL-JOB-ID   PIC X(255).
      *
       PROCEDURE DIVISION.
      *
       END PROGRAM RESPREC.
