package com.flashcards.core.importer

import com.flashcards.core.model.CardDraft
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportPlannerTest {

    private fun parsed(line: Int, front: String, back: String, tags: List<String> = emptyList()) =
        ParsedCard(line, CardDraft(front, back, tags))

    @Test
    fun `invalid lines are reported and valid ones kept in order`() {
        val plan = ImportPlanner.plan(
            listOf(parsed(2, "Hello", "Olá"), parsed(3, " ", "x"), parsed(4, "Bye", ""), parsed(5, "Thanks", "Obrigado")),
        )

        assertEquals(listOf("Hello", "Thanks"), plan.cards.map { it.front })
        assertEquals(
            listOf(
                ImportIssue(3, "Preencha a frente do cartão."),
                ImportIssue(4, "Preencha o verso do cartão."),
            ),
            plan.issues,
        )
        assertEquals(0, plan.duplicates)
    }

    @Test
    fun `duplicates inside the file and against the deck are skipped`() {
        val plan = ImportPlanner.plan(
            listOf(parsed(1, "Hello", "Olá"), parsed(2, "  hello ", "Oi"), parsed(3, "House", "Casa")),
            existingFronts = listOf("HOUSE"),
        )

        assertEquals(listOf("Hello"), plan.cards.map { it.front })
        assertEquals(2, plan.duplicates)
    }

    @Test
    fun `duplicates can be kept on request`() {
        val plan = ImportPlanner.plan(
            listOf(parsed(1, "Hello", "Olá"), parsed(2, "hello", "Oi")),
            skipDuplicates = false,
        )

        assertEquals(2, plan.cards.size)
        assertEquals(0, plan.duplicates)
    }

    @Test
    fun `cards come out normalized`() {
        val plan = ImportPlanner.plan(listOf(parsed(1, " Hello ", " Olá ", listOf("a", "A"))))

        assertEquals(CardDraft("Hello", "Olá", listOf("a")), plan.cards.single())
    }

    @Test
    fun `duplicate key ignores case and repeated spaces`() {
        assertEquals(ImportPlanner.duplicateKey("good   Morning "), ImportPlanner.duplicateKey("Good morning"))
    }
}
