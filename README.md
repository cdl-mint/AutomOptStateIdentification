## Automatic Optimization of Tolerance Ranges for Model-Driven Runtime State Identification

This repository contains artifacts and evaluation data related to the submission "Automatic Optimization of Tolerance Ranges for Model-Driven Runtime State Identification" submitted to IEEE Transactions on Automation Science and Engineering. The work adresses the uncertainty in sensor measurements when the aim is a seamless mapping from raw sensor data recorded from a running system (grip-arm robot) to the abstract-logical level where the system workflow is defined. It provides an automatic approach based on metaheuristic search for reconstructing state traces from recorded sensor data under measurement uncertainty, namely by deriving appropriate tolerance ranges that enable the identification of states.

### Structure

Artifacts are deposited as follows:

- Raw sensor data + Reference files: **[lib](lib)**
- System models (BDD + SM): **[model](model)**
- Evaluation results 
    - summary statistics, plots, logs: **[submission_results/<case>](submission_results)**
    - significance tests: **[submission_results/tests](submission_results/tests)**
