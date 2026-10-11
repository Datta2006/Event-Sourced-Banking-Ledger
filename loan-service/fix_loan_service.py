#!/usr/bin/env python3

with open('src/main/java/com/bank/loan/service/LoanService.java', 'r') as f:
    lines = f.readlines()

# Find the line with the TODO
for i, line in enumerate(lines):
    if 'TODO: Publish LoanApproved and LoanDisbursed events to Kafka' in line:
        # Replace the TODO and the following lines with proper event publishing
        lines[i] = '            String loanApprovedEvent = String.format(\n'
        lines.insert(i+1, '                    \"{\\\"loanId\\\":\\\"%s\\\",\\\"accountId\\\":\\\"%s\\\",\\\"amount\\\":%s,\\\"trustTier\\\":%d}\",\n')
        lines.insert(i+2, '                    loanLoanRequest.getLoanId(),\n')
        lines.insert(i+3, '                    loanLoanRequest.getAccountId(),\n')
        lines.insert(i+4, '                    loanLoanRequest.getAmount(),\n')
        lines.insert(i+5, '                    loanLoanRequest.getTrustTier()\n')
        lines.insert(i+6, '            );\n')
        lines.insert(i+7, '            kafkaTemplate.send(\"loan.events\", loanLoanRequest.getAccountId().toString(), loanApprovedEvent);\n')
        lines.insert(i+8, '\n')
        lines.insert(i+9, '            String loanDisbursedEvent = String.format(\n')
        lines.insert(i+10, '                    \"{\\\"loanId\\\":\\\"%s\\\",\\\"accountId\\\":\\\"%s\\\",\\\"amount\\\":%s,\\\"disbursedAt\\\":\\\"%s\\\"}\",\n')
        lines.insert(i+11, '                    loanLoanRequest.getLoanId(),\n')
        lines.insert(i+12, '                    loanLoanRequest.getAccountId(),\n')
        lines.insert(i+13, '                    loanLoanRequest.getAmount(),\n')
        lines.insert(i+14, '                    OffsetDateTime.now()\n')
        lines.insert(i+15, '            );\n')
        lines.insert(i+16, '            kafkaTemplate.send(\"loan.events\", loanLoanRequest.getAccountId().toString(), loanDisbursedEvent);\n')
        lines.insert(i+17, '\n')
        # Remove the old status updates
        del lines[i+18]  # loanLoanRequest.setStatus("APPROVED");
        del lines[i+18]  # loanLoanRequest.setStatus("DISBURSED");
        del lines[i+18]  # loanRequestRepository.save(loanLoanRequest);
        del lines[i+18]  # loanRequestRepository.save(loanLoanRequest);
        break

with open('src/main/java/com/bank/loan/service/LoanService.java', 'w') as f:
    f.writelines(lines)
