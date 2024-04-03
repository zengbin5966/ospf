package fuookami.ospf.kotlin.example.core_demo

import fuookami.ospf.kotlin.utils.math.*
import fuookami.ospf.kotlin.utils.error.*
import fuookami.ospf.kotlin.utils.concept.*
import fuookami.ospf.kotlin.utils.functional.*
import fuookami.ospf.kotlin.utils.multi_array.*
import fuookami.ospf.kotlin.core.frontend.variable.*
import fuookami.ospf.kotlin.core.frontend.expression.monomial.*
import fuookami.ospf.kotlin.core.frontend.expression.polynomial.*
import fuookami.ospf.kotlin.core.frontend.expression.symbol.*
import fuookami.ospf.kotlin.core.frontend.inequality.*
import fuookami.ospf.kotlin.core.frontend.model.mechanism.*
import fuookami.ospf.kotlin.core.backend.plugins.scip.*

data object Demo3 {
    data class Product(
        override val index: Int,
        val minYield: Flt64
    ) : Indexed

    data class Material(
        override val index: Int,
        val cost: Flt64,
        val yieldValue: Map<Product, Flt64>
    ) : Indexed

    private val products: ArrayList<Product> = ArrayList()
    private val materials: ArrayList<Material> = ArrayList()

    private lateinit var x: UIntVariable1

    private lateinit var cost: LinearSymbol
    private lateinit var yieldSymbols: LinearSymbols1

    private val metaModel: LinearMetaModel = LinearMetaModel("demo3")

    private val subProcesses = arrayListOf(
        Demo3::initVariable,
        Demo3::initSymbol,
        Demo3::initObject,
        Demo3::initConstraint,
        Demo3::solve,
        Demo3::analyzeSolution
    )

    init {
        products.add(Product(0, Flt64(15000.0)))
        products.add(Product(1, Flt64(15000.0)))
        products.add(Product(2, Flt64(10000.0)))

        materials.add(
            Material(
                0, Flt64(115.0), mapOf(
                    products[0] to Flt64(30.0),
                    products[1] to Flt64(10.0)
                )
            )
        )
        materials.add(
            Material(
                1, Flt64(97.0), mapOf(
                    products[0] to Flt64(15.0),
                    products[2] to Flt64(20.0)
                )
            )
        )
        materials.add(
            Material(
                2, Flt64(82.0), mapOf(
                    products[1] to Flt64(25.0),
                    products[2] to Flt64(15.0)
                )
            )
        )
        materials.add(
            Material(
                3, Flt64(76.0), mapOf(
                    products[0] to Flt64(15.0),
                    products[1] to Flt64(15.0),
                    products[2] to Flt64(15.0)
                )
            )
        )
    }

    suspend operator fun invoke(): Try {
        for (process in subProcesses) {
            when (val result = process()) {
                is Failed -> {
                    return Failed(result.error)
                }

                else -> {}
            }
        }
        return ok
    }

    private suspend fun initVariable(): Try {
        x = UIntVariable1("x", Shape1(materials.size))
        for (c in materials) {
            x[c].name = "${x.name}_${c.index}"
        }
        metaModel.addVars(x)
        return ok
    }

    private suspend fun initSymbol(): Try {
        cost = LinearExpressionSymbol(sum(materials) { it.cost * x[it] }, "cost")
        metaModel.addSymbol(cost)

        yieldSymbols = LinearSymbols1("yield", Shape1(products.size)) { (p, _) ->
            val product = products[p]
            LinearExpressionSymbol(
                sum(materials.filter { it.yieldValue.contains(product) }) { m ->
                    m.yieldValue[product]!! * x[m]
                },
                "yieldProduct_${p}"
            )
        }
        metaModel.addSymbols(yieldSymbols)

        return ok
    }

    private suspend fun initObject(): Try {
        metaModel.minimize(LinearPolynomial(cost))
        return ok
    }

    private suspend fun initConstraint(): Try {
        for (p in products) {
            metaModel.addConstraint(yieldSymbols[p.index] geq p.minYield)
        }
        return ok
    }

    private suspend fun solve(): Try {
        metaModel.export("1.opm")

        val solver = SCIPLinearSolver()
        when (val ret = solver(metaModel)) {
            is Ok -> {
                metaModel.tokens.setSolution(ret.value.solution)
            }

            is Failed -> {
                return Failed(ret.error)
            }
        }
        return ok
    }

    private suspend fun analyzeSolution(): Try {
        val ret = HashMap<Material, UInt64>()
        for (token in metaModel.tokens.tokens) {
            if (token.result!! eq Flt64.one
                && token.variable.belongsTo(x)
            ) {
                ret[materials[token.variable.vectorView[0]]] = token.result!!.round().toUInt64()
            }
        }
        return ok
    }
}