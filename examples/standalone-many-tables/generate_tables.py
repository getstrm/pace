#!/usr/bin/env python3

import re
import random
from textwrap import dedent
random.seed(42)

pattern = re.compile(r"^(.*)([A-Z][^A-Z]+)$")
gptNames = """
EmployeeData
CustomerInfo
ProductCatalog
OrderDetails
VendorRecords
FinancialTransactions
InventoryManagement
ProjectStatus
SalesAnalytics
ServiceRequests
UserProfiles
ShippingLogistics
TaskAssignments
EquipmentInventory
FacilityMaintenance
TrainingSessions
EmployeeFeedback
CustomerFeedback
IncidentReports
WorkShiftSchedule
SupplierContracts
ProductionSchedule
MarketingCampaigns
QualityAssurance
EventManagement
BudgetPlanning
AssetManagement
ComplianceRecords
TimeTracking
HealthAndSafetyLogs
""".strip().split("\n")

columns = [
            "transactionid     int     not null",
            "userid            int     not null",
            "email             varchar not null",
            "age               int     not null",
            "brand             varchar not null",
            "transactionamount int     not null",
            "quantity int null",
            "id varchar null",
            "customer_id varchar null",
            "local bool not null default true",
            "country_code varchar null",
        ]

schemas = [
        "public",
        "production",
        "development",
        "acceptance",
        "analytics",
        "marketing",
        "sales",
        ]


def run():
    pairs = [pattern.match(n.strip()).groups() for n in gptNames]
    suffixes = set([p[1] for p in pairs])
    prefixes = set([p[0] for p in pairs])
    names = [p+s for p in prefixes for s in suffixes]
    for schema in schemas:
        print("CREATE SCHEMA IF NOT EXISTS %s;" % (schema,))
        for n in names:
            print(createTable(schema, n))

def createTable(schema, name):
    col_ct = random.randint(1,20)
    cols = set([random.choice(columns) for _ in range(0,col_ct)])
    template = dedent(""" \
        CREATE TABLE IF NOT EXISTS %s.%s
        (
        %s
        );
    """) % (schema, name, ",\n".join(cols))
    return template

if __name__ == "__main__":
    run()
