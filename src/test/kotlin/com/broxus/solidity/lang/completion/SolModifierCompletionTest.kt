package com.broxus.solidity.lang.completion

class SolModifierCompletionTest : SolCompletionTestBase() {
  fun testModifierCompletion() = checkCompletion(hashSetOf("onlySeller"), """
        contract B {
            modifier onlySeller() {
                _;
            }

            function doit() /*caret*/ {
            }
        }
  """)

  fun testModifierCompletionIncomplete() = checkCompletion(hashSetOf("onlyDestroy"), """
        contract B {
            modifier onlyDestroy() {
                _;
            }

            function doit() /*caret*/
        }
  """)
}
