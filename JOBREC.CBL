IDENTIFICATION DIVISION.
       PROGRAM-ID. JOBREC.
       AUTHOR. Agent.
       DATE-WRITTEN. 2024-07-15.
      *
       DATA DIVISION.
       FILE SECTION.
      *
       WORKING-STORAGE SECTION.
      *
       01 JOB-REC.
          05 JOB-NAME             PIC X(255).
          05 JOB-DATA-COUNT       PIC 9(4) COMP.
          05 JOB-DATA-ITEMS OCCURS 0 TO 99 TIMES
                            DEPENDING ON JOB-DATA-COUNT.
             10 JOB-DATA-KEY      PIC X(50).
             10 JOB-DATA-VALUE    PIC X(255).
          05 JOB-METADATA-COUNT   PIC 9(4) COMP.
          05 JOB-METADATA-ITEMS OCCURS 0 TO 99 TIMES
                            DEPENDING ON JOB-METADATA-COUNT.
             10 JOB-METADATA-KEY  PIC X(50).
             10 JOB-METADATA-VALUE PIC X(255).
      *
       PROCEDURE DIVISION.
      *
       END PROGRAM JOBREC.
