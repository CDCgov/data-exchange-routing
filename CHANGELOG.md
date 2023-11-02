# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

### [0.0.31 2023-11-01

  - Created first draft of Metadata and reviewed internally. (Reviewing with wider audience next sprint)
  - Updated Routing function to use storage connection strings.
  - Updated Terraform and deployed routing Azure resources for TST environment.
  - Requested two Resource Groups to isolate routing resources in DEV and TST
  - Agreement with Upload on how to integrate the two systems:
  	  -  DEX Routing will create a storage account where Upload will copy any routable files into.

### [0.0.30] - 2023-10-18
  - No Code Changes
  - Creating analysis for bringing the spike work into production grade.

### [0.0.29] - 2023-09-20

  - Refactored repository to organize modules into spikes, tools, deprecated
  - Refactored code to abide by DEX standards
  - Added Unit tests
