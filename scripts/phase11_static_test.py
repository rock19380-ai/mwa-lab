import unittest

from phase11_static import REPAIR_COLUMNS, validate_repairs


class RepairLedgerTest(unittest.TestCase):
    def setUp(self):
        self.header = "\t".join(REPAIR_COLUMNS) + "\n"
        self.path = "app/src/main/repair.kt"
        self.commit = "a" * 40
        self.row = "\t".join(("repair-001", "P1", "broken", "reproduce", self.path,
                              "blocks demo", "minimal", "unit", "device", self.commit,
                              "accepted")) + "\n"

    def test_unchanged_source_requires_empty_ledger(self):
        validate_repairs(set(), self.header, {})

    def test_unapproved_drift_is_rejected(self):
        with self.assertRaisesRegex(AssertionError, "unapproved production drift"):
            validate_repairs({self.path}, self.header, {})

    def test_committed_p1_repair_is_accepted(self):
        validate_repairs({self.path}, self.header + self.row, {self.commit: {self.path}})

    def test_fake_commit_is_rejected(self):
        with self.assertRaisesRegex(AssertionError, "repair commit"):
            validate_repairs({self.path}, self.header + self.row, {self.commit: set()})

    def test_p2_is_rejected(self):
        with self.assertRaisesRegex(AssertionError, "P0/P1"):
            validate_repairs({self.path}, self.header + self.row.replace("\tP1\t", "\tP2\t"),
                             {self.commit: {self.path}})

    def test_stale_repair_is_rejected(self):
        with self.assertRaisesRegex(AssertionError, "no current protected drift"):
            validate_repairs(set(), self.header + self.row, {self.commit: {self.path}})


if __name__ == "__main__":
    unittest.main()
